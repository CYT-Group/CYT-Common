import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from zlib import decompress

DEPLOY = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DEPLOY))
import deploy
import import_test
import promote_test
import provision
from promotion_snapshot import CONFIG_TABLES, fernet


class PromotionTests(unittest.TestCase):
    def test_source_scopes_copy_to_test_configuration_and_separates_values(self):
        key = fernet(b'fixture-test-master')
        env = json.dumps(['ENV=test', 'CREDENTIAL_MASTER_KEY=fixture-test-master',
                          'ADMIN_PASSWORD=fixture-admin', 'DISCORD_ACCOUNTS=demo']).encode()
        source = [(table, None) for table in CONFIG_TABLES]
        source += [
            ('gateway_environment', {'key': 'test', 'is_production': False, 'base_urls_json': '{}'}),
            ('gateway_environment', {'key': 'local', 'is_production': False, 'base_urls_json': '{}'}),
            ('gateway_grant', {'id': 7}),
        ]
        for name, value, environment in (
            ('NAS_MINIO_ENDPOINT', 'https://files.example.test', 'test'),
            ('WEWORK_APP_FINANCIAL_SECRET', 'fixture-finance', 'test'),
            ('NAS_MINIO_SECRET_KEY', 'fixture-private', 'local'),
        ):
            source.append(('gateway_secret', {'id': 1, 'name': name, 'env': environment,
                                               'ciphertext': key.encrypt(value.encode()).decode()}))
        rows = '\n'.join(json.dumps(row) for row in source).encode()
        with patch.object(promote_test, 'remote', side_effect=(env, rows)) as remote:
            tables, configs, credentials = promote_test.read_test(
                'fixture', deploy.database_configuration, deploy.sensitive_environment)
        self.assertEqual(set(CONFIG_TABLES), tables.keys())
        self.assertEqual('production', tables['gateway_environment'][0]['key'])
        self.assertEqual([{'id': 7}], tables['gateway_grant'])
        self.assertEqual([], tables['gateway_secret'])
        self.assertEqual({'DISCORD_ACCOUNTS', 'NAS_MINIO_ENDPOINT', 'CRED_SCOPE_WECOM_FINANCIAL'}, configs.keys())
        self.assertEqual({'WEWORK_APP_FINANCIAL_SECRET': 'fixture-finance'}, credentials)
        query = remote.call_args.args[2]
        for table in ('gateway_call_log', 'gateway_admin_audit', 'gateway_idempotency', 'gateway_discord_event'):
            self.assertNotIn(table.encode(), query)

    def test_reuses_matching_value_and_only_encrypts_non_sensitive_values_in_snapshot(self):
        cloud = Mock()
        cloud.call.return_value = b'NAS_MINIO_SECRET_KEY\n'
        cloud.service.return_value = None
        cloud.version.side_effect = lambda ref, **_: ref.replace(':latest', ':2')
        payloads = {'NAS_MINIO_SECRET_KEY:2': b'fixture-existing',
                    'master:1': b'fixture-production-master',
                    'CYT_COMMON_TEST_PROMOTION_SNAPSHOT_KEY:1': b'fixture-wrap'}
        cloud.value.side_effect = payloads.__getitem__
        cloud.provision.side_effect = lambda name, payload, **options: name + ':1'
        args = SimpleNamespace(service='cyt-common', region='us-central1', secret=[], env=[],
                               service_account='runtime', migration_service_account='migration',
                               credential_master_key_secret='master:1')
        tables = {name: [] for name in CONFIG_TABLES}
        tables['gateway_environment'] = [{'key': 'production'}]
        configs = {'NAS_MINIO_ENDPOINT': ('https://files.example.test', {})}
        credentials = {'NAS_MINIO_SECRET_KEY': 'fixture-existing', 'CUSTOM_VENDOR_TOKEN': 'fixture-new'}
        bindings = {}
        with TemporaryDirectory(prefix='release-test-', dir=DEPLOY) as directory:
            path = Path(directory) / 'test-promotion.enc'
            promote_test.stage((tables, configs, credentials), path, args, cloud,
                               vars(provision), bindings, deploy.assignments)
            document = json.loads(decompress(fernet(b'fixture-wrap').decrypt(path.read_bytes())))
        self.assertEqual({'NAS_MINIO_SECRET_KEY': 'NAS_MINIO_SECRET_KEY:2',
                          'CUSTOM_VENDOR_TOKEN': 'CYT_COMMON_CUSTOM_VENDOR_TOKEN:1'}, bindings)
        self.assertNotIn('fixture-existing', repr(document))
        self.assertNotIn('fixture-new', repr(document))
        row, = document['tables']['gateway_secret']
        self.assertEqual('NAS_MINIO_ENDPOINT', row['name'])
        self.assertEqual(b'https://files.example.test',
                         fernet(b'fixture-production-master').decrypt(row['ciphertext'].encode()))
        cloud.grant_read.assert_any_call('CYT_COMMON_TEST_PROMOTION_SNAPSHOT_KEY:1', 'migration')

    def test_job_rejects_runtime_tables_before_touching_postgres(self):
        connection = Mock()
        tables = {name: [] for name in CONFIG_TABLES}
        tables['gateway_call_log'] = []
        with self.assertRaises(import_test.ImportFailure):
            import_test.apply_snapshot(connection, 'cyt_common', {'fingerprint': 'a' * 64, 'tables': tables})
        connection.transaction.assert_not_called()
