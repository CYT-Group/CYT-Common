#!/usr/bin/env python3
"""Run Common's versioned PostgreSQL changes before replacing the application."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess


FLYWAY_IMAGE = 'flyway/flyway:11.20.2-alpine'
MIGRATION_PATH = Path('CYT-Common-API/src/main/resources/db/migration')
LEGACY_MIGRATIONS = {
    'V1__gateway.sql': (1,),
    'V2__query_indexes.sql': (2,),
    'V3__wecom_approvals.sql': (3,),
    'V4__service_kind.sql': (4,),
    'V5__custom_response_contract.sql': (5,),
    'V6__discord_gateway.sql': (6,),
    'V7__payment_dispute_closures.sql': (7,),
}
NAME = re.compile(r'V(\d{8})_(\d{3})__([a-z][a-z0-9_]*)\.sql\Z')
COMMANDS = ('migrate', 'info', 'validate', 'lint')
FLYWAY_OPTIONS = (
    '-locations=filesystem:/flyway/sql', '-encoding=UTF-8',
    '-cleanDisabled=true', '-baselineOnMigrate=false',
    '-validateOnMigrate=true', '-validateMigrationNaming=true',
    '-failOnMissingLocations=true', '-outOfOrder=false',
    '-ignoreMigrationPatterns=', '-placeholderReplacement=false',
    '-connectRetries=10',
)


def check_migrations(directory):
    """Check filenames and encoding without Docker or a database connection."""
    directory = Path(directory)
    if not directory.is_dir():
        raise ValueError('缺少 SQL 迁移目录：' + str(directory))
    files = [path for path in directory.rglob('*')
             if path.is_file() and path.suffix.lower() == '.sql']
    if not files:
        raise ValueError('迁移目录中没有版本 SQL')
    versions = {}
    for path in files:
        if path.parent != directory:
            raise ValueError('SQL 必须直接放在迁移目录：' + path.name)
        if path.name in LEGACY_MIGRATIONS:
            version = LEGACY_MIGRATIONS[path.name]
        else:
            match = NAME.fullmatch(path.name)
            if not match:
                raise ValueError('保留既有 V1–V7；新增 SQL 命名为 VYYYYMMDD_NNN__英文说明.sql：' + path.name)
            datetime.strptime(match[1], '%Y%m%d')
            version = (int(match[1]), int(match[2]))
            if version[1] == 0:
                raise ValueError('迁移版本序号不能为 000：' + path.name)
        if version in versions:
            raise ValueError('迁移版本重复：' + path.name)
        content = path.read_bytes()
        if content.startswith(b'\xef\xbb\xbf'):
            raise ValueError('SQL 使用 UTF-8 无 BOM：' + path.name)
        text = content.decode('utf-8')
        if not text.strip():
            raise ValueError('迁移 SQL 不能为空：' + path.name)
        if '\r' in text:
            raise ValueError('SQL 使用 LF 换行：' + path.name)
        versions[version] = path
    return [versions[version] for version in sorted(versions)]


def database_environment(container):
    """Use the same PostgreSQL container as Compose; never print its environment."""
    result = subprocess.run(['docker', 'inspect', container], check=True,
                            capture_output=True, text=True, timeout=15)
    try:
        info = json.loads(result.stdout)[0]
        running = info['State']['Running']
        values = dict(item.split('=', 1) for item in info['Config']['Env'] if '=' in item)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as error:
        raise ValueError('无法读取数据库容器配置') from error
    if not running:
        raise ValueError('Common 数据库容器未运行')
    for key in ('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD'):
        if not values.get(key):
            raise ValueError('数据库容器缺少配置：' + key)
    database = values['POSTGRES_DB']
    if not re.fullmatch(r'[A-Za-z0-9_]+', database):
        raise ValueError('数据库名称只允许字母、数字和下划线')
    return {
        'FLYWAY_URL': 'jdbc:postgresql://127.0.0.1:5432/' + database,
        'FLYWAY_USER': values['POSTGRES_USER'],
        'FLYWAY_PASSWORD': values['POSTGRES_PASSWORD'],
    }


def flyway_command(container, directory, image, command):
    if command not in ('migrate', 'info', 'validate'):
        raise ValueError('只允许 migrate、info、validate；不提供 clean、repair 或 baseline')
    args = ['docker', 'run', '--rm', '--network', 'container:' + container,
            '--volume', str(Path(directory).resolve()) + ':/flyway/sql:ro']
    for key in ('FLYWAY_URL', 'FLYWAY_USER', 'FLYWAY_PASSWORD'):
        # Only variable names enter argv; credentials stay in the child environment.
        args.extend(['--env', key])
    return args + [image, *FLYWAY_OPTIONS, command]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--migrations-dir', type=Path)
    parser.add_argument('--database-container', default=os.environ.get(
        'COMMON_DATABASE_CONTAINER', 'cyt-common-java-db'))
    parser.add_argument('command', nargs='?', default='migrate', choices=COMMANDS)
    args = parser.parse_args(argv)
    directory = args.migrations_dir or args.root / MIGRATION_PATH
    files = check_migrations(directory)
    print('==> SQL 迁移目录：' + str(directory) + '；版本文件：' + str(len(files)), flush=True)
    if args.command == 'lint':
        return
    # A CI worker's unrelated FLYWAY_TARGET/SCHEMAS/etc must not alter the release.
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith('FLYWAY_')}
    environment.update(database_environment(args.database_container))
    image = os.environ.get('COMMON_FLYWAY_IMAGE', FLYWAY_IMAGE)
    subprocess.run(flyway_command(args.database_container, directory, image, args.command),
                   env=environment, check=True)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        # Do not serialize container inspection output or the credential environment.
        if isinstance(error, subprocess.CalledProcessError):
            detail = '子进程退出码 ' + str(error.returncode)
        else:
            detail = str(error)
        raise SystemExit('Common 数据库迁移失败：' + detail) from None
