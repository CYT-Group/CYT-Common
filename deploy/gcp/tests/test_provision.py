import json
from pathlib import Path
import runpy
from types import SimpleNamespace
import unittest
from unittest.mock import Mock


TOOL = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'provision.py'))


def binding(name, secret, version='latest'):
    return {'name': name, 'valueFrom': {'secretKeyRef': {'name': secret, 'key': version}}}


def service(*variables):
    return {'spec': {'template': {
        'metadata': {'annotations': {
            'run.googleapis.com/cloudsql-instances': 'cyt-digital:us-central1:cyt-db-primary'}},
        'spec': {'containers': [{'env': list(variables)}]},
    }}}


class ProvisionTests(unittest.TestCase):
    def test_ref_reuse_requires_the_same_database_destination_and_login(self):
        cloud = Mock(project='cyt-digital')
        legacy = service(binding('DATABASE_URL', 'CYT_DB_URL'),
                         binding('ADMIN_PASSWORD', 'COMMON_API-ADMIN_PASSWORD'),
                         {'name': 'ADMIN_USERNAME', 'value': 'fixture-admin'})
        cloud.service.return_value = legacy
        cloud.version.side_effect = lambda ref: ref.replace(':latest', ':1')
        payloads = {
            'CYT_DB_URL:1': b'postgresql+asyncpg://test:abc%40xyz@10.10.0.10/cyt',
            'PORTAL_OA_JDBC_URL:1': b'jdbc:postgresql://10.10.0.10/cyt?currentSchema=cyt_portal',
            'PORTAL_OA_DB_USERNAME:1': b'test',
            'PORTAL_OA_DB_PASSWORD:1': b'abc@xyz',
        }
        cloud.value.side_effect = payloads.__getitem__
        cloud.call.return_value = json.dumps({'region': 'us-central1', 'databaseVersion': 'POSTGRES_15',
            'ipAddresses': [{'type': 'PRIVATE', 'ipAddress': '10.10.0.10'}]}).encode()
        result = TOOL['legacy_reuse'](cloud, 'us-central1')
        self.assertEqual(TOOL['PORTAL_DATABASE'], result['database'])
        self.assertEqual(('ADMIN_PASSWORD', 'COMMON_API-ADMIN_PASSWORD:1'), result['administrator'])
        self.assertEqual('fixture-admin', result['username'])
        payloads['PORTAL_OA_DB_PASSWORD:1'] = b'wrong'
        with self.assertRaisesRegex(ValueError, '不一致'):
            TOOL['legacy_reuse'](cloud, 'us-central1')
        payloads['PORTAL_OA_DB_PASSWORD:1'] = b'abc@xyz'
        payloads['CYT_DB_URL:1'] = b'postgresql+asyncpg://test:abc%40xyz@10.10.0.20/cyt'
        with self.assertRaisesRegex(ValueError, '不一致'):
            TOOL['legacy_reuse'](cloud, 'us-central1')
        cloud.provision.assert_not_called()

    def test_existing_target_master_version_is_preserved_and_missing_jwt_is_created(self):
        cloud = Mock()
        cloud.service.return_value = service(binding('CREDENTIAL_MASTER_KEY', 'TARGET_MASTER', '9'))
        cloud.version.side_effect = lambda ref: ref
        cloud.provision.return_value = 'CYT_COMMON_JAVA_ADMIN_JWT_SECRET:1'
        args = SimpleNamespace(service='cyt-common-java', region='us-central1',
            service_account='fixture@cyt-digital.iam.gserviceaccount.com', admin_jwt_secret=None,
            credential_master_key_secret=None)

        TOOL['initialize_internal_secrets'](cloud, args)

        self.assertEqual('TARGET_MASTER:9', args.credential_master_key_secret)
        self.assertEqual('CYT_COMMON_JAVA_ADMIN_JWT_SECRET:1', args.admin_jwt_secret)
        cloud.provision.assert_called_once()
        self.assertGreater(len(cloud.provision.call_args.args[1]), 32)
        self.assertTrue(cloud.provision.call_args.kwargs['generated'])
        cloud.grant_read.assert_called_once_with(args.admin_jwt_secret, args.service_account)

    def test_unchanged_payload_reuses_version_and_changed_payload_goes_only_to_stdin(self):
        cloud = TOOL['Cloud'](['gcloud'], 'cyt-digital')
        cloud.call = Mock(return_value=b'projects/fixture/secrets/app/versions/4')
        cloud.version = Mock(return_value='app:3')
        cloud.value = Mock(return_value=b'old')
        self.assertEqual('app:3', cloud.provision('app', b'old'))
        self.assertEqual(1, cloud.call.call_count)

        self.assertEqual('app:4', cloud.provision('app', b'new'))
        self.assertIn('--data-file=-', cloud.call.call_args.args)
        self.assertNotIn('new', cloud.call.call_args.args)
        self.assertEqual(b'new', cloud.call.call_args.kwargs['payload'])


if __name__ == '__main__':
    unittest.main()
