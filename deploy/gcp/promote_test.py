"""Read the current Compose test environment and stage an encrypted production import."""
from hmac import compare_digest, digest
import json
from pathlib import Path
import re
import secrets
import shlex
import subprocess
from zlib import compress

from cryptography.fernet import InvalidToken

from promotion_snapshot import CONFIG_TABLES, fernet


TEST_APP = 'cyt-common-java'
TEST_DB = 'cyt-common-java-db'
PRODUCTION = 'production'
NAME = re.compile(r'[A-Z][A-Z0-9_]{0,63}\Z')
PSQL = ['docker', 'exec', '-i', TEST_DB, 'sh', '-c',
        'exec psql -X -qAt -v ON_ERROR_STOP=1 -v FETCH_COUNT=500 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
AUTH_URL = re.compile(r'://[^/\s]+@|[?&](?:token|secret|password|signature|access_token|api_key|client_secret|x-amz-signature)=', re.I)
AUTH_HEADERS = re.compile(r'^(?:authorization|proxy-authorization|cookie|.*(?:token|secret|password|api[-_]key|signature))$', re.I)
SENSITIVE_VALUE = re.compile(r'"(?:private_key|client_secret|refresh_token|access_token)"|-----BEGIN [^\n]*PRIVATE KEY-----', re.I)
REUSE_ALIASES = {
    'WEWORK_APP_FINANCIAL_SECRET': ('FINANCE_WECOM_ENGINE_SECRET',),
    'LINGXING_DEFAULT_APP_SECRET': ('LINGXING_APP_SECRET',),
    'MABANG_SECRET': ('BUYER-MABANG_SECRET', 'mabang-secret'),
    'MABANG_PROXY_TOKEN': ('mabang-proxy-token',),
    'DOCUSIGN_INTEGRATION_KEY': ('docusign-integration-key',),
    'DOCUSIGN_PRIVATE_KEY': ('docusign-private-key',),
    'DOCUSIGN_HMAC_KEY': ('docusign-hmac-key',),
    'WINIT_PROD_CLIENT_SECRET': ('CYT_DTC_WINIT_CLIENT_SECRET',),
    'WINIT_PROD_TOKEN': ('CYT_DTC_WINIT_TOKEN',),
    'WINIT_CLIENT_SECRET': ('CYT_DTC_WINIT_CLIENT_SECRET',),
    'WINIT_TOKEN': ('CYT_DTC_WINIT_TOKEN',),
}
SNAPSHOT_SQL = r"""
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '10min';
SET LOCAL TIME ZONE 'UTC';
SELECT format('SELECT json_build_array(%L, row_data) FROM (SELECT NULL::json AS row_data '
              'UNION ALL SELECT row_to_json(s) FROM %I.%I AS s) AS t;',
              tablename, schemaname, tablename)
FROM pg_tables
WHERE schemaname = 'public' AND tablename ~ '^gateway_[a-z0-9_]+$'
{where}
ORDER BY tablename
\gexec
COMMIT;
"""


def command_for(ssh_target, command):
    if ssh_target and not re.fullmatch(r'(?:[a-zA-Z0-9_][a-zA-Z0-9_.-]*@)?[a-zA-Z0-9][a-zA-Z0-9_.-]*', ssh_target):
        raise ValueError('测试服 SSH 目标须为主机别名或 user@host')
    if not ssh_target:
        return command
    return ['ssh', '-oBatchMode=yes', '-oStrictHostKeyChecking=yes',
            '-oConnectTimeout=10', ssh_target, shlex.join(command)]


def remote(ssh_target, command, payload=None):
    try:
        result = subprocess.run(command_for(ssh_target, command), input=payload,
                                capture_output=True, check=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        raise ValueError('读取测试容器失败，请检查预配的 SSH 登录和 Docker 权限') from None
    return result.stdout


def configuration_only(name):
    return (name.endswith('_MOCK') or name.startswith(('ADMIN_', 'POSTGRES_', 'SPRING_', 'JAVA_'))
            or name in {'ENV', 'HOST', 'PORT', 'DATABASE_URL', 'DB_SCHEMA', 'CREDENTIAL_MASTER_KEY',
                        'REDIS_URL', 'MOCK_ALL', 'GATEWAY_DEV_MODE', 'CUSTOM_API_ALLOW_LOOPBACK',
                        'DISCORD_GATEWAY_ENABLED', 'TEST_PROMOTION_SNAPSHOT_KEY'})


def database_value(value):
    return not AUTH_URL.search(value) and not SENSITIVE_VALUE.search(value)


def custom_auth(tables, credentials):
    """Move unambiguous legacy inline header authentication to the existing secret_ref field."""
    for table in ('gateway_module', 'gateway_custom_api', 'gateway_callback'):
        for row in tables.get(table, []):
            label = table + '#' + str(row['id'])
            for field in ('base_url', 'path', 'token_url', 'url'):
                if AUTH_URL.search(row.get(field) or ''):
                    raise ValueError(label + ' 的 URL 包含内嵌鉴权，需先改为凭据引用')
            if 'headers_json' not in row:
                continue
            headers = json.loads(row['headers_json'] or '{}')
            inline = [(key, value) for key, value in headers.items()
                      if AUTH_HEADERS.fullmatch(key) and value and not re.fullmatch(r'\{\{[^{}]+\}\}', str(value))]
            if not inline:
                continue
            if len(inline) != 1 or row['auth_type'] != 'none':
                raise ValueError(label + ' 的多重鉴权无法无损转成一个凭据引用')
            key, value = inline[0]
            name = 'CUSTOM_MIGRATED_' + table.removeprefix('gateway_').upper() + '_' + str(row['id'])
            if name in credentials:
                raise ValueError('迁移生成的凭据名已占用：' + name)
            credentials[name] = str(value)
            row.update(auth_type='header', auth_name=key, secret_ref=name)
            del headers[key]
            row['headers_json'] = json.dumps(headers, ensure_ascii=False)


def read_test(ssh_target, is_database_config, is_secret_config):
    raw = remote(ssh_target, ['docker', 'inspect', '--format', '{{json .Config.Env}}', TEST_APP])
    app = dict(item.split('=', 1) for item in json.loads(raw))
    current = app.get('ENV', 'test')
    if current not in {'test', 'testing'}:
        raise ValueError('测试容器当前环境不是 test 或 testing，停止迁移')
    master = fernet(app.get('CREDENTIAL_MASTER_KEY', '').encode())
    where = 'AND tablename IN (' + ','.join("'" + name + "'" for name in CONFIG_TABLES) + ')'
    raw = remote(ssh_target, PSQL, SNAPSHOT_SQL.format(where=where).encode())
    tables = {}
    for line in raw.splitlines():
        table, row = json.loads(line)
        if table not in CONFIG_TABLES:
            raise ValueError('测试快照包含未声明的业务表')
        records = tables.setdefault(table, [])
        if row is None or (table == 'gateway_environment' and row['key'] != current):
            continue
        if 'env' in row:
            if row['env'] != current:
                continue
            row['env'] = PRODUCTION
        records.append(row)
    missing = set(CONFIG_TABLES) - tables.keys()
    if missing:
        raise ValueError('测试库缺少配置表：' + ', '.join(sorted(missing)))
    active = tables['gateway_environment']
    if len(active) != 1 or active[0]['is_production']:
        raise ValueError('测试库缺少有效的当前测试环境记录')
    active[0].update(key=PRODUCTION, label='生产环境', is_production=True, enabled=True)
    if not database_value(active[0]['base_urls_json']):
        raise ValueError('测试环境的域名覆盖包含内嵌鉴权，需先拆分凭据')
    tables['gateway_environment'] = active
    configs = {}
    credentials = {}
    for name, value in app.items():
        if not value or not NAME.fullmatch(name) or configuration_only(name):
            continue
        if is_database_config(name):
            configs[name] = (value, {})
        elif is_secret_config(name):
            credentials[name] = value
    for row in tables['gateway_secret']:
        name = row['name']
        if not NAME.fullmatch(name):
            raise ValueError('测试库存在无效的凭据变量名')
        if configuration_only(name):
            continue
        try:
            value = master.decrypt(row['ciphertext'].encode('ascii')).decode('utf-8')
        except (InvalidToken, UnicodeError):
            raise ValueError('无法解密测试凭据：' + name) from None
        if is_database_config(name):
            configs[name] = (value, row)
        else:
            credentials[name] = value
    tables['gateway_secret'] = []
    if 'WEWORK_APP_FINANCIAL_SECRET' in credentials:
        configs.setdefault('CRED_SCOPE_WECOM_FINANCIAL', ('strict', {}))
    for name, (value, _) in configs.items():
        if not database_value(value):
            raise ValueError('非敏感配置包含鉴权数据，需先拆分凭据：' + name)
    custom_auth(tables, credentials)
    for name, value in credentials.items():
        if not value.strip() or len(value.encode('utf-8')) > 65536:
            raise ValueError('测试凭据为空或超过 Secret Manager 大小限制：' + name)
    return tables, configs, credentials


def reusable_reference(cloud, candidates, payload):
    for reference in dict.fromkeys(candidates):
        current = cloud.version(reference, optional=True)
        if current and compare_digest(cloud.value(current), payload):
            return current
    return None


def equivalent_ids(name):
    yield name
    yield from REUSE_ALIASES.get(name, ())
    quickcep = re.fullmatch(r'QUICKCEP_([A-Z0-9_]+)_(ACCESS_ID|PUBLIC_KEY)', name)
    if quickcep:
        yield 'cs_quickcep_' + '_'.join(quickcep.groups()).lower()
    shopify = re.fullmatch(r'SHOPIFY_CS_([A-Z0-9_]+)_ACCESS_TOKEN', name)
    if shopify:
        slug = shopify[1].lower().replace('_', '-')
        yield f'shopify-token-{slug}'
        yield f'shopify-{slug}-token'
        yield f'marketing-shopify-{slug}-token'


def stage(snapshot, path, args, cloud, provision, bindings, assignments):
    tables, configs, credentials = snapshot
    ids = provision['service_secret_id']
    configured = assignments(args.secret, 'secret')
    if set(credentials) & assignments(args.env, 'env').keys():
        raise ValueError('测试凭据与显式普通环境变量重叠')
    available = {line.rsplit('/', 1)[-1] for line in cloud.call(
        'secrets', 'list', '--format=value(name)').decode('utf-8').splitlines()}
    current = cloud.service(args.service, args.region, optional=True)
    current_env = provision['service_settings'](current) if current else {}
    pinned = {}
    for name, value in sorted(credentials.items()):
        if name in bindings or name in configured:
            reference = cloud.version(bindings.get(name) or configured[name])
            cloud.grant_read(reference, args.service_account)
            if name in configured:
                configured[name] = reference
            else:
                bindings[name] = reference
            pinned[name] = reference
            continue
        payload = value.encode('utf-8')
        candidates = [provision['binding'](current_env, name)]
        candidates += [entry + ':latest' for entry in equivalent_ids(name) if entry in available]
        reference = reusable_reference(cloud, filter(None, candidates), payload)
        if reference is None:
            reference = cloud.provision(ids(args.service, name), payload)
        cloud.grant_read(reference, args.service_account)
        pinned[name] = bindings[name] = reference
    args.secret = [name + '=' + reference for name, reference in configured.items()]

    # The app and the imported DB values must use the same fixed master-key version.
    args.credential_master_key_secret = cloud.version(args.credential_master_key_secret)
    db_key = fernet(cloud.value(args.credential_master_key_secret))
    identity = {'bindings': pinned, 'configs': {name: item[0] for name, item in configs.items()},
                'master': args.credential_master_key_secret,
                'tables': {name: sorted(json.dumps({k: v for k, v in row.items() if k != 'last_used_at'},
                                                   sort_keys=True) for row in rows)
                           for name, rows in tables.items()}}
    for name, (value, previous) in sorted(configs.items()):
        row = {**previous, 'env': PRODUCTION, 'name': name,
               'ciphertext': db_key.encrypt(value.encode()).decode('ascii')}
        row.pop('id', None)
        tables['gateway_secret'].append(row)
    # An authenticated fingerprint is stable across retries and hides short configuration values.
    reference = cloud.provision(ids(args.service, 'TEST_PROMOTION_SNAPSHOT_KEY'),
                                secrets.token_urlsafe(48).encode(), generated=True)
    cloud.grant_read(reference, args.migration_service_account or args.service_account)
    snapshot_key = cloud.value(reference)
    fingerprint = digest(snapshot_key, json.dumps(identity, sort_keys=True).encode(), 'sha256').hex()
    document = json.dumps({'fingerprint': fingerprint, 'tables': tables}, ensure_ascii=False)
    path.write_bytes(fernet(snapshot_key).encrypt(compress(document.encode('utf-8'))))
    args.promotion_key = reference
    args.promotion_snapshot = path.relative_to(Path(__file__).resolve().parents[2]).as_posix()
    print(f'测试快照已准备：{len(tables)} 张配置表，{sum(map(len, tables.values())):,} 条记录，'
          f'{len(configs)} 个入库字段，{len(pinned)} 个密钥绑定')
