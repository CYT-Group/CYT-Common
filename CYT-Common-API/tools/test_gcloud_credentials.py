import base64
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from cryptography.fernet import Fernet

spec = importlib.util.spec_from_file_location('gcloud_credentials', Path(__file__).with_name('gcloud_credentials.py'))
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class CredentialImportTest(unittest.TestCase):
    def test_allowlist_excludes_personal_login_and_unrelated_app_secrets(self):
        self.assertTrue(tool.allowed('DOCUSIGN_PRIVATE_KEY'))
        self.assertTrue(tool.allowed('QUICKCEP_EPOMAKER_ACCESS_ID'))
        self.assertFalse(tool.allowed('CREDENTIAL_MASTER_KEY'))
        self.assertFalse(tool.allowed('ADMIN_JWT_SECRET'))
        self.assertFalse(tool.allowed('DATABASE_URL'))
        self.assertFalse(tool.allowed('CYT_PRD_JWT_SECRET'))

    def test_plan_pins_versions_and_never_downloads_payloads(self):
        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(project='unit-project', service='gateway', region='unit-region', plan_file=Path(directory) / 'plan.json')
            def cloud(*arguments, **kwargs):
                self.assertFalse(kwargs.get('payload', False))
                if arguments[:3] == ('secrets', 'versions', 'describe'):
                    return {'name': 'projects/unit/secrets/key/versions/7', 'state': 'ENABLED'}
                return [{'name': 'projects/unit/secrets/docusign-private-key'}, {'name': 'projects/unit/secrets/DATABASE_URL'}]
            with patch.object(tool, 'cloud_environment', return_value=[]), patch.object(tool, 'gcloud', side_effect=cloud), contextlib.redirect_stdout(io.StringIO()):
                tool.plan(args)
            result = json.loads(args.plan_file.read_text())
            self.assertEqual(1, len(result['bindings']))
            self.assertEqual('DOCUSIGN_PRIVATE_KEY', result['bindings'][0]['name'])
            self.assertEqual('7', result['bindings'][0]['version'])

    def test_non_loopback_database_is_rejected_before_connecting(self):
        with patch('psycopg.connect') as connect:
            with self.assertRaises(tool.ImportFailure):
                tool.local_connection({'spring.datasource.url': 'jdbc:postgresql://remote.example/cyt_common_api'})
            connect.assert_not_called()

    def test_postgres_environment_overrides_and_spring_placeholders_are_resolved(self):
        config = {'spring.datasource.url': '${POSTGRES_URL:jdbc:postgresql://127.0.0.1:5432/cyt_common_api_java}',
                  'spring.datasource.username': '${POSTGRES_USERNAME:cyt_gateway}',
                  'spring.datasource.password': '${POSTGRES_PASSWORD:}'}
        with patch.dict(tool.os.environ, {}, clear=True), patch('psycopg.connect') as connect:
            tool.local_connection(config)
            self.assertEqual('cyt_gateway', connect.call_args.kwargs['user'])
            self.assertEqual('', connect.call_args.kwargs['password'])
        with patch.dict(tool.os.environ, {'POSTGRES_URL': 'jdbc:postgresql://localhost:55432/cyt_common_test',
                                        'POSTGRES_USERNAME': 'unit-user', 'POSTGRES_PASSWORD': 'unit-password'}), patch('psycopg.connect') as connect:
            tool.local_connection(config)
            self.assertEqual(55432, connect.call_args.kwargs['port'])
            self.assertEqual('cyt_common_test', connect.call_args.kwargs['dbname'])
            self.assertEqual('unit-user', connect.call_args.kwargs['user'])
            self.assertEqual('unit-password', connect.call_args.kwargs['password'])

    def test_mysql_local_database_is_rejected_after_backend_migration(self):
        with patch('psycopg.connect') as connect:
            with self.assertRaises(tool.ImportFailure):
                tool.local_connection({'spring.datasource.url': 'jdbc:mysql://127.0.0.1/cyt_common_api'})
            connect.assert_not_called()

    def test_production_partition_requires_matching_plan_and_explicit_target(self):
        config = {'ENV': 'local'}
        args = SimpleNamespace(env='production', allow_production=False)
        with self.assertRaises(tool.ImportFailure):
            tool.validate_environment_target(config, {'target_environment': 'production'}, args)
        args.allow_production = True
        with self.assertRaises(tool.ImportFailure):
            tool.validate_environment_target(config, {}, args)
        with self.assertRaises(tool.ImportFailure):
            tool.validate_environment_target(config, {'target_environment': 'local'}, args)
        tool.validate_environment_target(config, {'target_environment': 'production'}, args)
        with self.assertRaises(tool.ImportFailure):
            tool.validate_environment_target({'ENV': 'production'}, {'target_environment': 'production'}, args)

    def test_mismatched_environment_plan_is_rejected_for_all_partitions(self):
        with self.assertRaises(tool.ImportFailure):
            tool.validate_environment_target({'ENV': 'local'}, {'target_environment': 'staging'}, SimpleNamespace(env='local'))

    def test_existing_different_local_value_is_preserved_and_payload_is_not_logged(self):
        master = 'unit-test-master-value'
        cipher = Fernet(base64.urlsafe_b64encode(hashlib.sha256(master.encode()).digest()))
        encrypted = cipher.encrypt(b'existing-fixture-value').decode()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'src/main/resources').mkdir(parents=True)
            (root / 'src/main/resources/contract.json').write_text('{"credentials":[{"vars":[{"name":"WEWORK_SECRET"}]}]}')
            (root / 'application-local.properties').write_text('ENV=local\nCREDENTIAL_MASTER_KEY=' + master + '\n')
            plan = root / 'plan.json'
            plan.write_text(json.dumps({'project': 'unit-project', 'service': 'gateway', 'region': 'unit-region', 'bindings': [{'name': 'WEWORK_SECRET', 'source': 'secret_manager', 'secret': 'key', 'version': '7'}]}))
            args = SimpleNamespace(project='unit-project', service='gateway', region='unit-region', plan_file=plan, env='local', overwrite=False)
            cursor = MagicMock()
            cursor.fetchone.side_effect = [(1,), (encrypted,), (42, encrypted)]
            connection = MagicMock()
            connection.cursor.return_value.__enter__.return_value = cursor
            output = io.StringIO()
            with patch.object(tool, 'ROOT', root), patch.object(tool, 'local_connection', return_value=connection), patch.object(tool, 'gcloud', return_value='new-fixture-value'), patch.dict(tool.os.environ, {'CREDENTIAL_MASTER_KEY': ''}), contextlib.redirect_stdout(output):
                tool.apply(args)
            statements = [call.args[0] for call in cursor.execute.call_args_list]
            self.assertFalse(any(statement.startswith(('UPDATE ', 'INSERT ')) for statement in statements))
            self.assertNotIn('new-fixture-value', output.getvalue())
            self.assertNotIn('existing-fixture-value', output.getvalue())
            self.assertNotIn(master, output.getvalue())
            connection.commit.assert_called_once()


if __name__ == '__main__':
    unittest.main()
