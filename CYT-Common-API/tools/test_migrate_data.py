from datetime import datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import unittest

from sqlalchemy import Boolean, Column, DateTime, MetaData, Table, Text

spec = importlib.util.spec_from_file_location('migrate_data', Path(__file__).with_name('migrate_data.py'))
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class MigrationCompatibilityTest(unittest.TestCase):
    def test_mysql_sources_are_supported_but_targets_must_be_separate_postgres_databases(self):
        source, target = tool.database_urls('mysql://unit@localhost/legacy',
                                           'postgresql://unit@localhost/cyt_common_new')
        self.assertEqual('mysql+pymysql', source.drivername)
        self.assertEqual('postgresql+psycopg', target.drivername)
        for invalid in ('mysql://localhost/cyt_common_new', 'postgresql://localhost/business'):
            with self.subTest(target=invalid), self.assertRaises(tool.MigrationFailure):
                tool.database_urls('sqlite:///source.db', invalid)
        with self.assertRaises(tool.MigrationFailure):
            tool.database_urls('postgresql://localhost/cyt_common_same',
                               'postgresql://127.0.0.1:5432/cyt_common_same')

    def test_mysql_booleans_and_naive_utc_dates_preserve_meaning_in_postgres(self):
        target = Table('target', MetaData(), Column('enabled', Boolean),
                       Column('created_at', DateTime(timezone=True)), Column('detail', Text))
        timestamp = datetime(2026, 11, 12, 1, 2, 3, 456789)
        result = tool.compatible_row({'enabled': 1, 'created_at': timestamp,
                                      'detail': {'name': 'fixture'}, 'old_only': 42}, target)
        self.assertIs(result['enabled'], True)
        self.assertEqual(timestamp.replace(tzinfo=timezone.utc), result['created_at'])
        self.assertEqual('{"name": "fixture"}', result['detail'])
        self.assertNotIn('old_only', result)
        with self.assertRaises(tool.MigrationFailure):
            tool.compatible_row({'enabled': 2}, target)

    def test_aware_timestamps_are_normalized_when_target_uses_naive_utc(self):
        target = Table('target', MetaData(), Column('created_at', DateTime()))
        value = datetime(2026, 11, 12, 8, tzinfo=timezone(timedelta(hours=8)))
        self.assertEqual(datetime(2026, 11, 12), tool.compatible_row({'created_at': value}, target)['created_at'])


if __name__ == '__main__':
    unittest.main()
