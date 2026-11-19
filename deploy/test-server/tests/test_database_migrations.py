from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


MODULE = runpy.run_path(str(Path(__file__).parents[1] / 'migrate-database.py'))


class MigrationPolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def write(self, name, content=b'SELECT 1;\n'):
        path = self.directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def inspect_result(self, *, running=True, database='cyt_common'):
        return SimpleNamespace(stdout=json.dumps([{
            'State': {'Running': running},
            'Config': {'Env': ['POSTGRES_DB=' + database, 'POSTGRES_USER=common',
                               'POSTGRES_PASSWORD=fixture=password']},
        }]))

    def test_legacy_and_new_versions_sort_in_execution_order(self):
        for name in reversed(tuple(MODULE['LEGACY_MIGRATIONS'])):
            self.write(name)
        self.write('V20261112_002__add_index.sql')
        self.write('V20261112_001__add_column.sql')
        files = MODULE['check_migrations'](self.directory)
        self.assertEqual(
            [*MODULE['LEGACY_MIGRATIONS'], 'V20261112_001__add_column.sql', 'V20261112_002__add_index.sql'],
            [file.name for file in files],
        )

    def test_current_repository_versions_pass_without_rewriting_them(self):
        root = Path(__file__).resolve().parents[3]
        files = MODULE['check_migrations'](root / MODULE['MIGRATION_PATH'])
        self.assertTrue(set(MODULE['LEGACY_MIGRATIONS']).issubset({f.name for f in files}))

    def test_duplicate_version_is_rejected(self):
        self.write('V20261112_001__first.sql')
        self.write('V20261112_001__second.sql')
        with self.assertRaisesRegex(ValueError, '重复'):
            MODULE['check_migrations'](self.directory)

    def test_invalid_names_dates_and_repeatables_are_rejected(self):
        for name in ['update.sql', 'R__seed.sql', 'V20261301_001__bad.sql',
                     'V20261112_000__bad.sql', 'V7__new_change.sql',
                     'V1__renamed_initial_schema.sql', 'V20261112_1__bad.sql']:
            with self.subTest(name=name):
                path = self.write(name)
                with self.assertRaises(ValueError):
                    MODULE['check_migrations'](self.directory)
                path.unlink()

    def test_nested_sql_is_rejected(self):
        self.write('nested/V20261112_001__change.sql')
        with self.assertRaisesRegex(ValueError, '直接放'):
            MODULE['check_migrations'](self.directory)

    def test_sql_encoding_content_and_line_endings_are_checked(self):
        for content in [b'\xef\xbb\xbfSELECT 1;\n', b'\xffSELECT 1;', b' \n', b'SELECT 1;\r\n']:
            with self.subTest(content=content):
                self.write('V20261112_001__change.sql', content)
                with self.assertRaises(ValueError):
                    MODULE['check_migrations'](self.directory)

    def test_missing_or_empty_directory_is_not_silently_skipped(self):
        for directory in [self.directory, self.directory / 'missing']:
            with self.assertRaises(ValueError):
                MODULE['check_migrations'](directory)

    def test_flyway_is_strict_and_password_is_not_in_command_line(self):
        cmd = MODULE['flyway_command']('common-db', self.directory, MODULE['FLYWAY_IMAGE'], 'migrate')
        self.assertIn('container:common-db', cmd)
        self.assertIn(str(self.directory.resolve()) + ':/flyway/sql:ro', cmd)
        self.assertIn('FLYWAY_PASSWORD', cmd)
        self.assertFalse(any(item.startswith('FLYWAY_PASSWORD=') for item in cmd))
        for option in ['-baselineOnMigrate=false', '-cleanDisabled=true',
                       '-validateOnMigrate=true', '-validateMigrationNaming=true',
                       '-failOnMissingLocations=true', '-outOfOrder=false',
                       '-ignoreMigrationPatterns=', '-placeholderReplacement=false']:
            self.assertIn(option, cmd)
        self.assertEqual('flyway/flyway:11.20.2-alpine', MODULE['FLYWAY_IMAGE'])

    def test_destructive_and_repair_commands_are_not_exposed(self):
        for command in ['clean', 'repair', 'baseline', 'undo', 'lint']:
            with self.subTest(command=command), self.assertRaises(ValueError):
                MODULE['flyway_command']('common-db', self.directory, 'flyway:test', command)

    def test_database_configuration_is_read_without_printing_password(self):
        with patch('subprocess.run', return_value=self.inspect_result()) as run:
            environment = MODULE['database_environment']('test-db')
        self.assertEqual('jdbc:postgresql://127.0.0.1:5432/cyt_common', environment['FLYWAY_URL'])
        self.assertEqual('common', environment['FLYWAY_USER'])
        self.assertEqual('fixture=password', environment['FLYWAY_PASSWORD'])
        self.assertEqual(['docker', 'inspect', 'test-db'], run.call_args.args[0])

    def test_stopped_database_and_unsafe_database_name_are_rejected(self):
        for result in [self.inspect_result(running=False), self.inspect_result(database='other?schema=public')]:
            with self.subTest(result=result.stdout), patch('subprocess.run', return_value=result):
                with self.assertRaises(ValueError):
                    MODULE['database_environment']('test-db')

    def test_missing_container_fields_are_rejected_without_leaking_environment(self):
        result = SimpleNamespace(stdout=json.dumps([{
            'State': {'Running': True}, 'Config': {'Env': ['UNRELATED_SECRET=fixture-private']},
        }]))
        with patch('subprocess.run', return_value=result), self.assertRaisesRegex(ValueError, 'POSTGRES_DB'):
            MODULE['database_environment']('unsupported-db')

    def test_invalid_container_response_is_rejected(self):
        for body in ['[]', '{}', '{invalid', '[{"State": {}}]']:
            with self.subTest(body=body), patch('subprocess.run', return_value=SimpleNamespace(stdout=body)):
                with self.assertRaises(ValueError):
                    MODULE['database_environment']('test-db')

    def test_lint_never_starts_a_subprocess(self):
        self.write('V20261112_001__change.sql')
        with patch('subprocess.run') as run, redirect_stdout(io.StringIO()):
            MODULE['main'](['--root', str(self.directory), '--migrations-dir', str(self.directory), 'lint'])
        run.assert_not_called()

    def test_ci_flyway_environment_cannot_redirect_or_truncate_release(self):
        self.write('V20261112_001__change.sql')
        with patch.dict(os.environ, {'FLYWAY_TARGET': '1', 'FLYWAY_SCHEMAS': 'other',
                                     'FLYWAY_PASSWORD': 'unrelated', 'COMMON_DATABASE_CONTAINER': 'fixture-db'}), \
                patch('subprocess.run', side_effect=[self.inspect_result(), SimpleNamespace(returncode=0)]) as run, \
                redirect_stdout(io.StringIO()):
            MODULE['main'](['--root', str(self.directory), '--migrations-dir', str(self.directory), 'migrate'])
        self.assertEqual('fixture-db', run.call_args_list[0].args[0][-1])
        child_environment = run.call_args.kwargs['env']
        self.assertEqual('fixture=password', child_environment['FLYWAY_PASSWORD'])
        self.assertNotIn('FLYWAY_TARGET', child_environment)
        self.assertNotIn('FLYWAY_SCHEMAS', child_environment)

    def test_sql_failure_is_returned_to_deployment_caller(self):
        self.write('V20261112_001__change.sql')
        failure = subprocess.CalledProcessError(7, ['docker', 'run'])
        with patch('subprocess.run', side_effect=[self.inspect_result(), failure]), redirect_stdout(io.StringIO()):
            with self.assertRaises(subprocess.CalledProcessError):
                MODULE['main'](['--root', str(self.directory), '--migrations-dir', str(self.directory), 'migrate'])


if __name__ == '__main__':
    unittest.main()
