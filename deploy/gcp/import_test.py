"""Import one authenticated test snapshot into a Flyway-initialized production schema."""
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import parse_qs, unquote, urlsplit
from zlib import decompress
import psycopg
from psycopg import sql
from promotion_snapshot import CONFIG_TABLES, fernet


class ImportFailure(Exception):
    """An error safe to show in Cloud Build and Cloud Run logs."""


def connections(jdbc, username, password):
    url = urlsplit(jdbc.removeprefix('jdbc:'))
    if (not jdbc.startswith('jdbc:postgresql://') or not url.hostname or not url.path[1:]
            or url.username or url.fragment):
        raise ImportFailure('导入需要 Flyway 使用的 PostgreSQL JDBC 地址')
    parameters = parse_qs(url.query)
    return dict(host=url.hostname, port=url.port or 5432, dbname=unquote(url.path[1:]),
                user=username, password=password, connect_timeout=15,
                sslmode=parameters.get('sslmode', ['prefer'])[0], options='-c timezone=UTC')


def qualified(schema, table):
    return sql.Identifier(schema, table)


def insert_rows(connection, schema, table, columns, rows):
    groups = {}
    for row in rows:
        names = tuple(sorted(row))
        if not names or not set(names).issubset(columns):
            raise ImportFailure('生产表与测试列结构不兼容：' + table)
        groups.setdefault(names, []).append(row)
    relation = qualified(schema, table)
    for names, group in groups.items():
        fields = sql.SQL(', ').join(map(sql.Identifier, names))
        query = sql.SQL('INSERT INTO {table} ({fields}) SELECT {fields} '
                        'FROM json_populate_recordset(NULL::{table}, %s::json)').format(
                            table=relation, fields=fields)
        for start in range(0, len(group), 500):
            batch = group[start:start + 500]
            count = connection.execute(query, (json.dumps(batch, ensure_ascii=False),)).rowcount
            if count != len(batch):
                raise ImportFailure('导入条数不一致：' + table)


def apply_snapshot(connection, schema, document):
    if not re.fullmatch(r'cyt_common(?:_[a-z][a-z0-9_]{0,41})?', schema):
        raise ImportFailure('迁移目标须为 Common 的生产 schema')
    fingerprint = document['fingerprint']
    if not re.fullmatch(r'[a-f0-9]{64}', fingerprint):
        raise ImportFailure('快照标识无效')
    tables = document['tables']
    if tables.keys() != set(CONFIG_TABLES):
        raise ImportFailure('快照包含日志或缺少配置表')
    marker = qualified(schema, 'common_test_promotion')
    with connection.transaction():
        connection.execute('SELECT pg_advisory_xact_lock(hashtext(%s), 90344)', (schema,))
        connection.execute(sql.SQL(
            'CREATE TABLE IF NOT EXISTS {} (id boolean PRIMARY KEY DEFAULT true CHECK (id), '
            'fingerprint text NOT NULL, imported_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP)'
        ).format(marker))
        prior = connection.execute(sql.SQL('SELECT fingerprint FROM {}').format(marker)).fetchone()
        if prior:
            if prior[0] != fingerprint:
                raise ImportFailure('生产已完成首次导入，测试配置或密钥版本已有变动；停止覆盖')
            return False
        available = {}
        for table, column in connection.execute(
            "SELECT table_name, column_name FROM information_schema.columns "
            "WHERE table_schema=%s AND table_name ~ '^gateway_[a-z0-9_]+$' ORDER BY 1, 2",
            (schema,),
        ):
            if table in tables:
                available.setdefault(table, set()).add(column)
        missing = tables.keys() - available.keys()
        if missing:
            raise ImportFailure('生产缺少测试业务表：' + ', '.join(sorted(missing)))
        relations = sql.SQL(', ').join(qualified(schema, table) for table in available)
        connection.execute(sql.SQL('LOCK TABLE {} IN SHARE ROW EXCLUSIVE MODE').format(relations))
        for table in available:
            relation = qualified(schema, table)
            where = sql.SQL('') if table != 'gateway_environment' else sql.SQL(
                "WHERE NOT (key='production' AND created_by='migration' AND is_production AND enabled "
                "AND base_urls_json='{}' AND extra_vars_json='[]')")
            exists = connection.execute(sql.SQL('SELECT EXISTS(SELECT 1 FROM {} {})').format(relation, where))
            if exists.fetchone()[0]:
                raise ImportFailure('生产已有数据，停止覆盖：' + table)
        connection.execute(sql.SQL("DELETE FROM {} WHERE key='production'").format(
            qualified(schema, 'gateway_environment')))
        for table in CONFIG_TABLES:
            insert_rows(connection, schema, table, available[table], tables[table])
        for table in tables:
            if 'id' not in available[table]:
                continue
            relation = qualified(schema, table)
            sequence, maximum = connection.execute(
                sql.SQL('SELECT pg_get_serial_sequence(%s, %s), MAX(id) FROM {}').format(relation),
                (relation.as_string(connection), 'id'),
            ).fetchone()
            if sequence and maximum is not None:
                connection.execute('SELECT setval(%s::regclass, %s)', (sequence, maximum))
        connection.execute(sql.SQL('INSERT INTO {} (fingerprint) VALUES (%s)').format(marker), (fingerprint,))
    return True


def main(path):
    parameters = connections(os.environ['FLYWAY_URL'], os.environ['FLYWAY_USER'], os.environ['FLYWAY_PASSWORD'])
    key = fernet(os.environ['PROMOTION_SNAPSHOT_KEY'].encode())
    document = json.loads(decompress(key.decrypt(Path(path).read_bytes())))
    with psycopg.connect(**parameters) as connection:
        imported = apply_snapshot(connection, os.environ['PROMOTION_SCHEMA'], document)
    status = '测试数据已导入' if imported else '同一批配置已导入，跳过重复复制'
    print(f'{status}。快照包含 {len(document["tables"])} 张表。', flush=True)


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except ImportFailure as error:
        raise SystemExit('测试数据导入停止：' + str(error)) from None
    except Exception as error:
        # DB driver exceptions may include the values of rejected fields.
        raise SystemExit('测试数据导入失败：' + type(error).__name__) from None
