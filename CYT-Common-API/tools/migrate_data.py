"""Copy gateway data into an empty PostgreSQL deployment. Dry-run is the default."""
import argparse
import json
import os
import re
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, MetaData, Text, create_engine, func, inspect, select, text
from sqlalchemy.engine import make_url

TABLES = (
    'gateway_system', 'gateway_grant', 'gateway_call_log', 'gateway_grant_audit',
    'gateway_idempotency', 'gateway_system_apikey', 'gateway_admin_audit',
    'gateway_module', 'gateway_custom_api', 'gateway_op_category', 'gateway_op_order',
    'gateway_environment', 'gateway_secret', 'gateway_callback',
)
OPTIONAL_TABLES = (
    'gateway_wecom_form', 'gateway_wecom_submission', 'gateway_wecom_event',
    'gateway_discord_session', 'gateway_discord_event',
)


class MigrationFailure(Exception):
    """A deliberate validation message which contains no connection details or row data."""


def database_urls(source_url, target_url):
    if not source_url or not target_url:
        raise MigrationFailure('Set SOURCE_DATABASE_URL and TARGET_DATABASE_URL; passwords are never printed.')
    source = make_url(source_url)
    target = make_url(target_url)
    drivers = {'postgresql': 'postgresql+psycopg', 'mysql': 'mysql+pymysql', 'sqlite': 'sqlite'}
    if source.get_backend_name() not in drivers:
        raise MigrationFailure('Source must be PostgreSQL, MySQL or SQLite.')
    if target.get_backend_name() != 'postgresql' or not re.fullmatch(r'cyt_common[a-zA-Z0-9_]*', target.database or ''):
        raise MigrationFailure('Target must be an independent PostgreSQL database named cyt_common*.')
    source = source.set(drivername=drivers[source.get_backend_name()])
    target = target.set(drivername='postgresql+psycopg')
    def endpoint(url):
        host = url.host or ''
        if host in ('localhost', '127.0.0.1', '::1'):
            host = 'loopback'
        return url.get_backend_name(), host, url.port or 5432, url.database
    if endpoint(source) == endpoint(target):
        raise MigrationFailure('Source and target must be separate databases.')
    return source, target


def compatible_row(row, target_table):
    """MySQL integer booleans and naive UTC timestamps need explicit PostgreSQL types."""
    result = {}
    for column in target_table.c:
        if column.name not in row:
            continue
        value = row[column.name]
        if value is not None and isinstance(column.type, Boolean):
            if value not in (False, True, 0, 1):
                raise MigrationFailure('A source boolean is invalid; target transaction will roll back.')
            value = bool(value)
        elif isinstance(value, datetime) and isinstance(column.type, DateTime):
            if column.type.timezone and value.tzinfo is None:
                value = value.replace(tzinfo=timezone.utc)
            elif not column.type.timezone and value.tzinfo is not None:
                value = value.astimezone(timezone.utc).replace(tzinfo=None)
        elif isinstance(value, (dict, list)) and isinstance(column.type, Text):
            value = json.dumps(value, ensure_ascii=False)
        result[column.name] = value
    return result


def reset_sequences(connection, table):
    preparer = connection.dialect.identifier_preparer
    for column in table.primary_key:
        sequence = connection.scalar(
            text('SELECT pg_get_serial_sequence(:table, :column)'),
            {'table': preparer.format_table(table), 'column': column.name},
        )
        if sequence is None:
            continue
        maximum = connection.scalar(select(func.max(column)))
        connection.execute(
            text('SELECT setval(CAST(:sequence AS regclass), :value, :has_rows)'),
            {'sequence': sequence, 'value': max(1, maximum or 1), 'has_rows': maximum is not None},
        )


def migrate(apply: bool):
    source_url = os.environ.get('SOURCE_DATABASE_URL', '')
    target_url = os.environ.get('TARGET_DATABASE_URL', '')
    source, target = database_urls(source_url, target_url)
    schema = os.environ.get('SOURCE_DB_SCHEMA') or None
    if schema and source.get_backend_name() != 'postgresql':
        raise MigrationFailure('SOURCE_DB_SCHEMA only applies to PostgreSQL.')
    source_options = {'options': '-c timezone=UTC'} if source.get_backend_name() == 'postgresql' else {}
    if source.get_backend_name() == 'mysql':
        source_options = {'init_command': "SET time_zone = '+00:00'"}
    source_engine = create_engine(source, connect_args=source_options)
    target_engine = create_engine(target, connect_args={'options': '-c timezone=UTC'})
    try:
        with source_engine.connect() as source_connection:
            if source.get_backend_name() in ('postgresql', 'mysql'):
                source_connection = source_connection.execution_options(isolation_level='REPEATABLE READ')
            with source_connection.begin():
                if source.get_backend_name() in ('postgresql', 'mysql'):
                    source_connection.exec_driver_sql('SET TRANSACTION READ ONLY')
                else:
                    source_connection.exec_driver_sql('PRAGMA query_only = ON')
                available = set(inspect(source_connection).get_table_names(schema=schema))
                tables = TABLES + tuple(name for name in OPTIONAL_TABLES if name in available)
                source_metadata = MetaData(schema=schema)
                source_metadata.reflect(source_connection, only=tables)
                with target_engine.begin() as target_connection:
                    target_metadata = MetaData()
                    target_metadata.reflect(target_connection, only=TABLES + OPTIONAL_TABLES)
                    for name in TABLES + OPTIONAL_TABLES:
                        count = target_connection.scalar(select(func.count()).select_from(target_metadata.tables[name]))
                        if count:
                            raise MigrationFailure(f'Target table {name} contains data; refusing to overwrite.')
                    for name in tables:
                        source_table = source_metadata.tables[f'{schema}.{name}' if schema else name]
                        target_table = target_metadata.tables[name]
                        count = source_connection.scalar(select(func.count()).select_from(source_table))
                        print(f'{name}: {count} rows' + ('; copying' if apply else '; dry-run'))
                        if not apply:
                            continue
                        copied = 0
                        with source_connection.execution_options(stream_results=True).execute(select(source_table)) as stream:
                            for batch in stream.mappings().partitions(1000):
                                rows = [compatible_row(row, target_table) for row in batch]
                                if rows:
                                    target_connection.execute(target_table.insert(), rows)
                                    copied += len(rows)
                        actual = target_connection.scalar(select(func.count()).select_from(target_table))
                        if actual != count or copied != count:
                            raise RuntimeError(f'Row count mismatch in {name}; target transaction will roll back.')
                    if apply:
                        for table in target_metadata.sorted_tables:
                            reset_sequences(target_connection, table)
        print('Copy committed.' if apply else 'Preflight complete. No records copied. Use --apply only after reviewing the target.')
    finally:
        source_engine.dispose()
        target_engine.dispose()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Copy into the empty target; otherwise only inspect.')
    arguments = parser.parse_args()
    try:
        migrate(arguments.apply)
    except Exception as error:
        # Driver exceptions may contain DSNs or SQL payloads. Print only our deliberate validation messages.
        print(f'Migration failed: {error}' if isinstance(error, MigrationFailure) else f'Migration failed: {type(error).__name__}; target transaction rolled back if opened.')
        raise SystemExit(1)
