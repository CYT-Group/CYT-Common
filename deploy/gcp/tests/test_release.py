"""发布新鲜度、环境升级与 etag 切流；所有云调用均为 mock。"""
from contextlib import redirect_stdout
import importlib.util
import io
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError


DEPLOY = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('common_release', DEPLOY / 'release.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
BUILD = '12345678-1234-1234-1234-123456789abc'
STAMP = '2026-11-12T10:00:00Z'
RESOURCE = 'projects/cyt-digital/locations/us-central1/services/cyt-common-java'
IMAGE = 'gcr.io/cyt-digital/cyt-common-java@sha256:' + 'a' * 64
REVISION = MODULE.revision_name('cyt-common-java', BUILD)


def service():
    return {'name': RESOURCE, 'etag': 'etag-original', 'annotations': {'other.cyt.group/value': 'preserved'},
            'latestReadyRevision': RESOURCE + '/revisions/' + REVISION,
            'latestCreatedRevision': RESOURCE + '/revisions/' + REVISION,
            'template': {'containers': [{'env': [
                {'name': 'GMAIL_MOCK', 'value': 'true'},
                {'name': 'WECOM_APPROVALS_MOCK', 'valueSource': {'secretKeyRef': {'secret': 'OLD_MOCK', 'version': '1'}}},
                {'name': 'UNRELATED', 'value': 'preserve'},
            ]}]}}


def snapshot():
    return {'containers': [{'image': IMAGE, 'env': [{'name': 'COMMON_RELEASE_BUILD_ID', 'value': BUILD}]}]}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.digest_file = Path(self.temp.name) / 'digest.txt'
        self.digest_file.write_text(IMAGE + '\n', encoding='utf-8')

    def arguments(self):
        return SimpleNamespace(project='cyt-digital', region='us-central1', service='cyt-common-java', build_id=BUILD,
            image='gcr.io/cyt-digital/cyt-common-java:' + BUILD, digest_file=self.digest_file,
            secret_bindings_file='',
            gcloud_args=['--', '--update-env-vars=^|^ENV=production|MOCK_ALL=false|GMAIL_MOCK=false',
                         '--update-secrets=SPRING_DATASOURCE_URL=DB_URL:1,ADMIN_PASSWORD=ADMIN:1',
                         '--remove-env-vars=POSTGRES_URL,SPRING_DATASOURCE_URL,ADMIN_PASSWORD',
                         '--remove-secrets=POSTGRES_URL', '--quiet'])

    def test_existing_service_deploys_no_traffic_by_digest_then_conditionally_promotes(self):
        with patch.object(MODULE, 'cli', side_effect=[STAMP, 'fixture-token', '']) as cli:
            with patch.object(MODULE, 'api', side_effect=[service(), service(), snapshot(), {'done': True}]) as api:
                with redirect_stdout(io.StringIO()):
                    MODULE.release(self.arguments())
        command = cli.call_args_list[2].args[0]
        self.assertIn('--no-traffic', command)
        self.assertIn('--image=' + IMAGE, command)
        for flag in ['--remove-env-vars=', '--remove-secrets=']:
            names = next(value[len(flag):] for value in command if value.startswith(flag)).split(',')
            self.assertIn('GMAIL_MOCK', names)
            self.assertIn('WECOM_APPROVALS_MOCK', names)
            self.assertNotIn('UNRELATED', names)
        runtime = next(value.split('=', 1)[1] for value in command if value.startswith('--update-env-vars='))
        self.assertIn('COMMON_RELEASE_BUILD_ID=' + BUILD, runtime)
        self.assertNotIn('GMAIL_MOCK=', runtime)
        update = api.call_args_list[-1]
        self.assertEqual('PATCH', update.args[0])
        body = update.args[3]
        self.assertEqual('etag-original', body['etag'])
        self.assertEqual({'updateMask': 'traffic,annotations'}, update.kwargs['query'])
        self.assertEqual({'other.cyt.group/value': 'preserved', MODULE.BUILD_TIME: STAMP, MODULE.BUILD_ID: BUILD}, body['annotations'])
        self.assertEqual(REVISION, body['traffic'][0]['revision'])
        self.assertEqual(100, body['traffic'][0]['percent'])

    def test_manifest_converts_existing_literal_to_versioned_secret(self):
        current = service()
        current['template']['containers'][0]['env'].append(
            {'name': 'QUICKCEP_SHOP_ACCESS_ID', 'value': 'legacy-fixture'})
        args = self.arguments()
        args.secret_bindings_file = 'deploy/gcp/unit-bindings.json'
        with patch.dict(MODULE.DEPLOY, {'load_secret_bindings': lambda _: {
                'QUICKCEP_SHOP_ACCESS_ID': 'common-prod-quickcep-shop-access-id:7'}}):
            with patch.object(MODULE, 'cli', side_effect=[STAMP, 'fixture-token', '']) as cli:
                with patch.object(MODULE, 'api', side_effect=[current, service(), snapshot(), {'done': True}]):
                    with redirect_stdout(io.StringIO()):
                        MODULE.release(args)
        command = cli.call_args_list[2].args[0]
        self.assertTrue(any('QUICKCEP_SHOP_ACCESS_ID=common-prod-quickcep-shop-access-id:7' in arg
                            for arg in command if arg.startswith('--update-secrets=')))
        self.assertTrue(any('QUICKCEP_SHOP_ACCESS_ID' in arg
                            for arg in command if arg.startswith('--remove-env-vars=')))
        self.assertFalse(any('legacy-fixture' in arg for arg in command))

    def test_first_service_creation_omits_unsupported_no_traffic_flag(self):
        with patch.object(MODULE, 'cli', side_effect=[STAMP, 'fixture-token', '']) as cli:
            with patch.object(MODULE, 'api', side_effect=[None, service(), snapshot(), {'done': True}]):
                with redirect_stdout(io.StringIO()) as output:
                    MODULE.release(self.arguments())
        self.assertNotIn('--no-traffic', cli.call_args_list[2].args[0])
        self.assertIn('首次创建须串行', output.getvalue())

    def test_older_build_stops_before_revision_deployment(self):
        current = service()
        current['annotations'].update({MODULE.BUILD_TIME: '2026-11-12T11:00:00Z', MODULE.BUILD_ID: 'newer-build'})
        with patch.object(MODULE, 'cli', side_effect=[STAMP, 'fixture-token']) as cli:
            with patch.object(MODULE, 'api', return_value=current) as api:
                with self.assertRaisesRegex(MODULE.ReleaseFailure, '旧构建'):
                    MODULE.release(self.arguments())
        self.assertEqual(2, cli.call_count)
        self.assertEqual(1, api.call_count)

    def test_newer_build_promoted_during_deploy_blocks_old_build_promotion(self):
        current = service()
        current['annotations'].update({MODULE.BUILD_TIME: '2026-11-12T11:00:00Z', MODULE.BUILD_ID: 'newer-build'})
        with patch.object(MODULE, 'api') as api:
            with self.assertRaisesRegex(MODULE.ReleaseFailure, '旧构建'):
                MODULE.promote(current, REVISION, IMAGE, 'token', STAMP, BUILD)
        api.assert_not_called()

    def test_target_revision_changed_while_deploying_never_changes_traffic(self):
        current = service()
        current['latestCreatedRevision'] = RESOURCE + '/revisions/other-build'
        with patch.object(MODULE, 'api') as api:
            with self.assertRaisesRegex(MODULE.ReleaseFailure, '最新就绪'):
                MODULE.promote(current, REVISION, IMAGE, 'token', STAMP, BUILD)
        api.assert_not_called()

    def test_wrong_digest_or_build_marker_never_changes_traffic(self):
        for wrong in [dict(snapshot(), containers=[{'image': IMAGE + 'wrong'}]),
                      {'containers': [{'image': IMAGE, 'env': [{'name': 'COMMON_RELEASE_BUILD_ID', 'value': 'other'}]}]}]:
            with self.subTest(wrong=wrong), patch.object(MODULE, 'api', return_value=wrong) as api:
                with self.assertRaises(MODULE.ReleaseFailure):
                    MODULE.promote(service(), REVISION, IMAGE, 'token', STAMP, BUILD)
                self.assertEqual(1, api.call_count)

    def test_etag_conflicts_are_not_retried(self):
        for status in [409, 412]:
            error = HTTPError('https://run.googleapis.com', status, 'conflict', {}, None)
            with self.subTest(status=status), patch.object(MODULE, 'urlopen', side_effect=error) as request:
                with self.assertRaisesRegex(MODULE.ReleaseFailure, str(status)):
                    MODULE.api('PATCH', RESOURCE, 'fixture-token', {'etag': 'stale'})
                self.assertEqual(1, request.call_count)

    def test_missing_etag_stops_before_update(self):
        current = service()
        current.pop('etag')
        with patch.object(MODULE, 'api', return_value=snapshot()) as api:
            with self.assertRaisesRegex(MODULE.ReleaseFailure, 'etag'):
                MODULE.promote(current, REVISION, IMAGE, 'token', STAMP, BUILD)
        self.assertEqual(1, api.call_count)

    def test_unrelated_traffic_tags_are_preserved_without_routing_normal_traffic(self):
        current = service()
        current['traffic'] = [{'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': 'old', 'percent': 100, 'tag': 'rollback'}]
        with patch.object(MODULE, 'api', side_effect=[snapshot(), {'done': True}]) as api:
            MODULE.promote(current, REVISION, IMAGE, 'token', STAMP, BUILD)
        targets = api.call_args.args[3]['traffic']
        self.assertEqual({'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': 'old', 'percent': 0, 'tag': 'rollback'}, targets[1])

    def test_long_service_names_still_produce_a_valid_unique_revision_name(self):
        name = 'c' * 49
        revision = MODULE.revision_name(name, BUILD)
        self.assertLessEqual(len(revision), 63)
        self.assertNotEqual(revision, MODULE.revision_name(name, BUILD.replace('a', 'b')))

    def test_mismatched_digest_file_stops_without_any_cloud_call(self):
        self.digest_file.write_text('gcr.io/other/service@sha256:' + 'a' * 64, encoding='utf-8')
        with patch.object(MODULE, 'cli') as cli, patch.object(MODULE, 'api') as api:
            with self.assertRaisesRegex(MODULE.ReleaseFailure, 'digest'):
                MODULE.release(self.arguments())
        cli.assert_not_called()
        api.assert_not_called()


if __name__ == '__main__':
    unittest.main()
