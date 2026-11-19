"""Opt-in acceptance test: only touches the disposable PostgreSQL container it creates."""
import os
from pathlib import Path
import runpy
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


TOOLS = Path(__file__).resolve().parents[1]
MODULE = runpy.run_path(str(TOOLS / 'migrate-database.py'))


@unittest.skipUnless(os.environ.get('COMMON_RUN_FLYWAY_INTEGRATION') == '1',
                     '需显式开启独立 Docker / PostgreSQL 集成验收')
class FlywayIntegrationTests(unittest.TestCase):
    def test_empty_database_repeatability_history_validation_and_transaction_failure(self):
        name = 'cyt-common-migration-it-' + secrets.token_hex(4)
        environment = dict(os.environ, POSTGRES_DB='common_migration_test',
                           POSTGRES_USER='migration_test', POSTGRES_PASSWORD=secrets.token_urlsafe(24))
        subprocess.run(['docker', 'run', '-d', '--name', name, '--tmpfs', '/var/lib/postgresql/data',
                        '-e', 'POSTGRES_DB', '-e', 'POSTGRES_USER', '-e', 'POSTGRES_PASSWORD',
                        'postgres:17-alpine'], env=environment, check=True, capture_output=True)
        self.addCleanup(lambda: subprocess.run(['docker', 'rm', '-f', name], capture_output=True))

        def query(sql):
            result = subprocess.run(['docker', 'exec', '-i', name, 'sh', '-c',
                'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -h127.0.0.1 -U "$POSTGRES_USER" '
                '-d "$POSTGRES_DB" -X -q -A -t -v ON_ERROR_STOP=1'],
                input=sql, text=True, capture_output=True)
            if result.returncode:
                raise RuntimeError('Disposable PostgreSQL query failed')
            return result.stdout.strip()

        for _ in range(60):
            try:
                query('SELECT 1;')
                break
            except RuntimeError:
                time.sleep(1)
        else:
            self.fail('隔离 PostgreSQL 未就绪')

        with tempfile.TemporaryDirectory(prefix='common-flyway-it-') as temporary:
            directory = Path(temporary)
            directory.chmod(0o755)
            for path in (TOOLS.parents[1] / MODULE['MIGRATION_PATH']).glob('*.sql'):
                shutil.copyfile(path, directory / path.name)
            self.assertTrue(set(MODULE['LEGACY_MIGRATIONS']).issubset({p.name for p in directory.glob('*.sql')}))
            create = directory / 'V20990101_001__create_integration_probe.sql'
            create.write_text('CREATE TABLE migration_probe (id INT PRIMARY KEY);\n', encoding='utf-8', newline='\n')
            insert = directory / 'V20990101_002__insert_integration_probe.sql'
            insert.write_text('INSERT INTO migration_probe(id) VALUES (1);\n', encoding='utf-8', newline='\n')

            def run(command):
                return subprocess.run([sys.executable, '-B', str(TOOLS / 'migrate-database.py'),
                    '--root', temporary, '--migrations-dir', temporary, '--database-container', name,
                    command], capture_output=True, text=True)

            def success(command):
                result = run(command)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)

            query('CREATE TABLE unmanaged_probe(id INT PRIMARY KEY);')
            self.assertNotEqual(0, run('migrate').returncode, '非空未纳管数据库不能自动 baseline')
            query('DROP TABLE unmanaged_probe;')
            success('migrate')
            expected_versions = len(list(directory.glob('*.sql')))
            self.assertEqual(str(expected_versions), query('SELECT COUNT(*) FROM flyway_schema_history WHERE success;'))
            self.assertGreaterEqual(int(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name LIKE 'gateway_%';")), 19)
            self.assertEqual('0', query('SELECT COUNT(*) FROM gateway_system;'), '数据库升级不灌演示业务数据')
            success('migrate')
            self.assertEqual('1', query('SELECT COUNT(*) FROM migration_probe;'), '成功版本不得重复执行')

            original = create.read_text(encoding='utf-8')
            create.write_text(original + 'ALTER TABLE migration_probe ADD COLUMN tampered INT;\n', encoding='utf-8', newline='\n')
            self.assertNotEqual(0, run('validate').returncode, '修改已执行 SQL 必须阻断部署')
            create.write_text(original, encoding='utf-8', newline='\n')
            saved = insert.read_text(encoding='utf-8')
            insert.unlink()
            self.assertNotEqual(0, run('validate').returncode, '缺失已执行 SQL 必须阻断部署')
            insert.write_text(saved, encoding='utf-8', newline='\n')
            success('validate')

            (directory / 'V20990101_003__invalid_sql.sql').write_text(
                'CREATE TABLE migration_rolled_back(id INT);\nTHIS IS INVALID SQL;\n', encoding='utf-8', newline='\n')
            (directory / 'V20990101_004__must_not_run.sql').write_text(
                'INSERT INTO migration_probe(id) VALUES (2);\n', encoding='utf-8', newline='\n')
            self.assertNotEqual(0, run('migrate').returncode, 'SQL 失败必须返回非零状态')
            self.assertEqual('1', query('SELECT COUNT(*) FROM migration_probe;'), '失败不能继续执行后续版本')
            self.assertEqual('0', query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='migration_rolled_back';"),
                             'PostgreSQL 失败迁移必须回滚本版本的 DDL')


if __name__ == '__main__':
    unittest.main()
