#!/usr/bin/env python3
"""Google CLI 发布 Common：构建同源 JAR 镜像 → Flyway Job → Cloud Run。"""
import argparse
from contextlib import ExitStack
from functools import lru_cache
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
SECRET_REF = re.compile(r'[A-Za-z0-9_-]{1,255}:(?:latest|[1-9][0-9]*)\Z')
ACCOUNT = re.compile(r'[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\.gserviceaccount\.com\Z')
ENV_NAME = re.compile(r'[A-Z][A-Z0-9_]*\Z')
SENSITIVE_NAME = re.compile(r'(?:^|_)(?:PASSWORD|PASSWORD_HASH|SECRET|TOKEN|API_KEY|ACCESS_KEY|PRIVATE_KEY|MASTER_KEY|CREDENTIALS|SA_JSON)$')
AUTH_URL = re.compile(r'://[^/\s]+@|[?&](?:token|secret|password|signature|access_token|api_key|client_secret|x-amz-signature)=', re.I)
RESERVED_ALIASES = {
    'PORT', 'POSTGRES_URL', 'POSTGRES_USERNAME', 'POSTGRES_PASSWORD',
    'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_APPLICATION_JSON',
    'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'REDIS_URL',
    'COMMON_RELEASE_BUILD_ID',
    'SPRING_DATASOURCE_HIKARI_SCHEMA', 'SPRING_DATASOURCE_HIKARI_JDBC_URL',
    'SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL',
}
STALE_ALIASES = RESERVED_ALIASES - {'PORT', 'REDIS_URL', 'COMMON_RELEASE_BUILD_ID'}
BOOTSTRAP_OPTIONS = ('db_url_secret', 'db_user_secret', 'db_password_secret',
                     'admin_jwt_secret', 'credential_master_key_secret')
BOOTSTRAP_NAMES = {
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'ADMIN_PASSWORD', 'ADMIN_PASSWORD_HASH', 'ADMIN_JWT_SECRET', 'CREDENTIAL_MASTER_KEY',
}


@lru_cache(maxsize=1)
def credential_patterns():
    directory = ROOT / 'CYT-Common-API/src/main/resources'
    extra = json.loads((directory / 'credential-storage.json').read_text(encoding='utf-8'))
    private = set(extra['secret_manager'])
    public = set(extra['database_config'])
    public.update(('CRED_SCOPE_{SYSTEM}', 'CRED_SPLIT_{SYSTEM}', 'CRED_ACCOUNTS_{SYSTEM}'))
    for filename in ('contract.json', 'business-contract.json', 'discord-contract.json'):
        contract = json.loads((directory / filename).read_text(encoding='utf-8'))
        for platform in contract.get('credentials', []):
            public.update(value for key in ('accounts_var', 'default_account_var')
                          if (value := platform.get(key)))
            for variable in platform['vars']:
                names = private if variable.get('secret') else public
                name = variable['name']
                names.update((name, name.replace('{ACCOUNT}_', '')))
                if variable.get('scoped'):
                    names.add(variable['scoped'])

    def compile_names(names):
        return tuple(re.compile(re.escape(name).replace(r'\{SYSTEM\}', '[A-Z0-9_]+')
                                .replace(r'\{ACCOUNT\}', '[A-Z0-9_]+')) for name in sorted(names))

    return compile_names(private), compile_names(public)


def sensitive_environment(name):
    private, _ = credential_patterns()
    return (name in {'REDIS_URL', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME'}
            or name.startswith('CUSTOM_') or SENSITIVE_NAME.search(name) is not None
            or any(pattern.fullmatch(name) for pattern in private))


def database_configuration(name):
    _, public = credential_patterns()
    return not sensitive_environment(name) and any(pattern.fullmatch(name) for pattern in public)


def dictionary_flag(values):
    """使用 gcloud 分隔符保留 JSON、逗号及中文；所有参数直接通过 argv 传递。"""
    for delimiter in ('|', '~', '@', ';', '%'):
        if not any(delimiter in key or delimiter in value for key, value in values.items()):
            return '^' + delimiter + '^' + delimiter.join(key + '=' + value for key, value in values.items())
    raise ValueError('参数包含过多分隔字符，无法编码为 gcloud 参数')


def assignments(items, kind):
    values = {}
    for item in items:
        key, separator, value = item.partition('=')
        if not separator or not ENV_NAME.fullmatch(key) or any(c in value for c in ('\n', '\r', '\0')):
            raise ValueError(kind + ' 参数应为单行 ENV_NAME=VALUE')
        if key in values:
            raise ValueError(kind + ' 参数重复：' + key)
        if kind == 'secret' and not SECRET_REF.fullmatch(value):
            raise ValueError('Secret 应为名称:版本，不接受明文值：' + key)
        if kind == 'secret' and database_configuration(key):
            raise ValueError('非敏感项应在数据库中配置：' + key)
        if kind == 'env' and (sensitive_environment(key) or AUTH_URL.search(value)):
            raise ValueError('敏感变量请使用 --secret 引用 Secret Manager：' + key)
        if kind == 'env' and key.endswith('_MOCK') and value.lower() in ('1', 'true', 'yes', 'on'):
            raise ValueError('生产发布不能开启平台模拟：' + key)
        values[key] = value
    return values


def load_secret_bindings(filename):
    if not filename:
        return {}
    if not re.fullmatch(r'deploy/gcp/[A-Za-z0-9_-]+\.json', filename):
        raise ValueError('Secret 绑定清单必须位于 deploy/gcp/，并使用简单的 JSON 文件名')
    path = (ROOT / filename).resolve()
    if path.parent != (ROOT / 'deploy/gcp').resolve():
        raise ValueError('Secret 绑定清单路径无效')

    def unique(entries):
        result = dict(entries)
        if len(result) != len(entries):
            raise ValueError('Secret 绑定清单中包含重复变量')
        return result

    try:
        values = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)
    except (UnicodeError, json.JSONDecodeError):
        raise ValueError('Secret 绑定清单必须是有效的 UTF-8 JSON') from None
    if not isinstance(values, dict):
        raise ValueError('Secret 绑定清单必须是变量名到 Secret 引用的 JSON 对象')
    for name, ref in values.items():
        if not ENV_NAME.fullmatch(name):
            raise ValueError('Secret 绑定清单包含非法变量名')
        if not isinstance(ref, str) or not SECRET_REF.fullmatch(ref) or ref.endswith(':latest'):
            raise ValueError('Secret 绑定清单只允许固定数字版本的引用：' + name)
        if database_configuration(name):
            raise ValueError('非敏感项应在数据库中配置：' + name)
    return values


def substitutions(args):
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,28}[a-z0-9]', args.project):
        raise ValueError('无效的 GCP project ID')
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,47}[a-z0-9]|[a-z]', args.service):
        raise ValueError('服务名应为不超过 49 位的小写字母、数字和连字符')
    if args.service == 'cyt-common-api':
        raise ValueError('cyt-common-api 是现有 Python 网关，请使用独立 Java 服务名，如 cyt-common-java')
    if not re.fullmatch(r'[a-z]+-[a-z]+[0-9]+', args.region):
        raise ValueError('无效的 GCP region')
    if not re.fullmatch(r'cyt_common(?:_[a-z][a-z0-9_]{0,41})?', args.db_schema):
        raise ValueError('schema 必须使用 Common 专属名称 cyt_common 或 cyt_common_<环境>')
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,23}[a-z0-9]|[a-z]', args.vpc_connector):
        raise ValueError('请提供同区域 VPC 连接器的短名称')
    for account in (args.service_account, args.migration_service_account or args.service_account,
                    args.build_service_account or args.service_account):
        if not account or not ACCOUNT.fullmatch(account):
            raise ValueError('服务账号应为 gserviceaccount.com 邮箱')
    if bool(args.admin_password_secret) == bool(args.admin_password_hash_secret):
        raise ValueError('请配置管理员密码或 SHA-256 摘要中的一项')
    admin_variable = 'ADMIN_PASSWORD' if args.admin_password_secret else 'ADMIN_PASSWORD_HASH'
    admin_reference = args.admin_password_secret or args.admin_password_hash_secret
    other_admin = {'ADMIN_PASSWORD', 'ADMIN_PASSWORD_HASH'} - {admin_variable}
    references = [getattr(args, option) for option in BOOTSTRAP_OPTIONS] + [admin_reference]
    if args.redis_secret:
        references.append(args.redis_secret)
    if any(not value or not SECRET_REF.fullmatch(value) for value in references):
        raise ValueError('数据库、管理员和加密密钥均应使用 Secret 名称:版本格式')
    if not 1 <= args.max_instances <= 100:
        raise ValueError('max-instances 必须在 1–100 之间')
    if args.max_instances > 1 and not args.redis_secret:
        raise ValueError('多实例发布必须通过 --redis-secret 配置共享配额后端')
    registry = args.registry or 'gcr.io/' + args.project
    if not re.fullmatch(r'(?:[a-z0-9-]+\.)?gcr\.io/[a-z0-9-]+|[a-z0-9-]+-docker\.pkg\.dev/[a-z0-9-]+/[a-z0-9_-]+', registry):
        raise ValueError('镜像仓库应为 gcr.io/项目或区域-docker.pkg.dev/项目/仓库')
    job_prefix = args.migration_job_prefix or args.service[:20].rstrip('-') + '-db'
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,24}[a-z0-9]|[a-z]', job_prefix):
        raise ValueError('迁移 Job 前缀最多 26 位，以预留构建 ID')

    environment = {
        'SPRING_PROFILES_ACTIVE': 'prod', 'SPRING_FLYWAY_ENABLED': 'false', 'HOST': '0.0.0.0',
        'ENV': 'production', 'MOCK_ALL': 'false', 'GATEWAY_DEV_MODE': 'false',
        'COMMON_DATABASE_SCHEMA': args.db_schema,
        'JAVA_SEED_ENABLED': 'false', 'ADMIN_DEV_LOGIN': 'false', 'CUSTOM_API_ALLOW_LOOPBACK': 'false',
        'DISCORD_GATEWAY_ENABLED': 'true' if args.discord_listener else 'false',
    }
    secrets = {
        'SPRING_DATASOURCE_URL': args.db_url_secret, 'SPRING_DATASOURCE_USERNAME': args.db_user_secret,
        'SPRING_DATASOURCE_PASSWORD': args.db_password_secret, admin_variable: admin_reference,
        'ADMIN_JWT_SECRET': args.admin_jwt_secret, 'CREDENTIAL_MASTER_KEY': args.credential_master_key_secret,
    }
    if args.redis_secret:
        secrets['REDIS_URL'] = args.redis_secret
    extra_env, extra_secrets = assignments(args.env, 'env'), assignments(args.secret, 'secret')
    managed = set(environment) | BOOTSTRAP_NAMES | RESERVED_ALIASES
    bindings = load_secret_bindings(args.secret_bindings_file)
    if (managed & (extra_env.keys() | extra_secrets.keys() | bindings.keys())
            or extra_env.keys() & extra_secrets.keys()
            or bindings.keys() & (extra_env.keys() | extra_secrets.keys())):
        raise ValueError('额外参数不能覆盖部署入口管理的生产、数据库或运行配置')
    environment.update(extra_env)
    secrets.update(extra_secrets)
    migration_secrets = {'FLYWAY_URL': args.db_url_secret, 'FLYWAY_USER': args.db_user_secret,
                         'FLYWAY_PASSWORD': args.db_password_secret}
    promotion_key = getattr(args, 'promotion_key', '')
    values = {
        '_SERVICE': args.service, '_REGION': args.region, '_REGISTRY': registry,
        '_DB_SCHEMA': args.db_schema,
        '_JOB_PREFIX': job_prefix, '_VPC_CONNECTOR': args.vpc_connector, '_VPC_EGRESS': args.vpc_egress,
        '_RUNTIME_SA': args.service_account,
        '_MIGRATION_SA': args.migration_service_account or args.service_account,
        '_MIGRATION_SECRETS': dictionary_flag(migration_secrets),
        '_RUNTIME_SECRETS': dictionary_flag(secrets), '_RUNTIME_ENV': dictionary_flag(environment),
        '_SECRET_BINDINGS_FILE': args.secret_bindings_file,
        '_PROMOTION_SNAPSHOT': getattr(args, 'promotion_snapshot', ''),
        '_PROMOTION_SECRETS': (
            dictionary_flag({**migration_secrets, 'PROMOTION_SNAPSHOT_KEY': promotion_key}) if promotion_key else ''),
        '_AUTH_FLAG': '--allow-unauthenticated' if args.public else '--no-allow-unauthenticated',
        '_MIN_INSTANCES': '1' if args.discord_listener else '0', '_MAX_INSTANCES': str(args.max_instances),
        '_CPU_FLAG': '--no-cpu-throttling' if args.discord_listener else '--cpu-throttling',
        '_REMOVE_ENV_VARS': ','.join(sorted(STALE_ALIASES | set(secrets) | other_admin)),
        '_REMOVE_SECRET_VARS': ','.join(sorted(STALE_ALIASES | other_admin)),
    }
    if any(len(value.encode('utf-8')) > 4000 for value in values.values()):
        raise ValueError('Cloud Build 单个 substitution 不能超过 4000 字节，请缩短额外配置')
    return values


def gcloud_command():
    executable = shutil.which('gcloud')
    if not executable:
        raise ValueError('未找到 gcloud，请先安装 Google Cloud CLI 并登录')
    path = Path(executable)
    if os.name == 'nt' and path.suffix.lower() in ('.cmd', '.bat', '.ps1'):
        sdk = path.resolve().parent.parent
        entry = sdk / 'lib/gcloud.py'
        if not entry.is_file():
            raise ValueError('找不到 Google Cloud SDK 的 lib/gcloud.py')
        bundled = sdk / 'platform/bundledpython/python.exe'
        return [str(bundled) if bundled.is_file() else sys.executable, str(entry)]
    return [str(path)]


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('--project', default='cyt-digital')
    result.add_argument('--region', default='us-central1')
    result.add_argument('--service', default='cyt-common-java', help='独立的 Java 服务名；拒绝覆盖 cyt-common-api')
    result.add_argument('--service-account')
    result.add_argument('--migration-service-account')
    result.add_argument('--build-service-account')
    result.add_argument('--vpc-connector', default='cyt-vpc-connector')
    result.add_argument('--vpc-egress', choices=('all-traffic', 'private-ranges-only'), default='all-traffic')
    result.add_argument('--registry', help='默认 gcr.io/项目；可指定已有 Artifact Registry 仓库')
    result.add_argument('--migration-job-prefix')
    result.add_argument('--db-schema', default='cyt_common', help='目标 PostgreSQL 数据库内由 Flyway 创建的独立 schema')
    result.add_argument('--db-url-secret', help='PostgreSQL JDBC URL Secret 名称:版本')
    result.add_argument('--db-user-secret')
    result.add_argument('--db-password-secret')
    result.add_argument('--admin-password-secret')
    result.add_argument('--admin-password-hash-secret')
    result.add_argument('--admin-jwt-secret')
    result.add_argument('--credential-master-key-secret')
    result.add_argument('--reuse-common-api', action='store_true', help='核验并复用旧服务的数据库连接及管理员登录')
    result.add_argument('--inspect-common-api', action='store_true', help='只输出旧服务可复用的 Secret 版本清单')
    result.add_argument('--promote-test', action='store_true', help='首次上线时迁入当前测试环境的数据和配置')
    result.add_argument('--inspect-test-promotion', action='store_true', help='只读核对测试配置与凭据数量')
    result.add_argument('--test-ssh', default=os.environ.get('COMMON_TEST_SSH', 'lrb@192.168.1.182'),
                        help='测试环境 SSH 目标，默认使用当前测试服务器')
    result.add_argument('--secret-from-env', action='append', default=[], metavar='ENV_NAME=SECRET_ID',
                        help='从发布进程环境写入上游 Secret，复用内容相同的版本')
    result.add_argument('--redis-secret', help='可选；多实例时必须显式配置')
    result.add_argument('--max-instances', type=int, default=1)
    result.add_argument('--discord-listener', action='store_true', help='启用后台监听，并配置常驻实例及持续 CPU')
    result.add_argument('--env', action='append', default=[], metavar='NAME=VALUE')
    result.add_argument('--secret', action='append', default=[], metavar='ENV=NAME:VERSION')
    result.add_argument('--secret-bindings-file', default='', metavar='deploy/gcp/NAME.json')
    result.add_argument('--public', action='store_true', help='允许浏览器到达服务，网关与管理端仍执行应用认证')
    result.add_argument('--dry-run', action='store_true', help='仅校验本地 SQL 并输出发布计划，不调用云端')
    return result


def provision_arguments(args):
    planned = {}
    used_ids = set()
    reserved = BOOTSTRAP_NAMES | RESERVED_ALIASES | {'ADMIN_USERNAME'}
    explicit = assignments(args.secret, 'secret')
    manifest = load_secret_bindings(args.secret_bindings_file)
    overrides = explicit.keys() | manifest.keys() | assignments(args.env, 'env').keys()
    prefix = args.service.replace('-', '_').upper() + '_'
    protected_ids = {prefix + name for name in reserved}
    protected_ids.add(prefix + 'TEST_PROMOTION_SNAPSHOT_KEY')
    protected_ids.update(ref.split(':', 1)[0] for ref in [*explicit.values(), *manifest.values()])
    for assignment in args.secret_from_env:
        name, separator, identifier = assignment.partition('=')
        if not separator or not ENV_NAME.fullmatch(name) or not re.fullmatch(r'[A-Za-z0-9_-]{1,255}', identifier):
            raise ValueError('自动写入格式为 ENV_NAME=SECRET_ID')
        if (name in reserved or name in overrides or name in planned or identifier in used_ids
                or database_configuration(name)):
            raise ValueError('自动写入变量重复、属于启动绑定或不应写入 Secret Manager：' + name)
        if not identifier.startswith(prefix) or len(identifier) == len(prefix) or identifier in protected_ids:
            raise ValueError('自动写入只能使用当前服务的专属 Secret ID 前缀 ' + prefix + '：' + identifier)
        planned[name] = identifier
        used_ids.add(identifier)
    return planned


def validate_reuse_arguments(args):
    database = [getattr(args, option) for option in BOOTSTRAP_OPTIONS[:3]]
    if any(database) and not all(database):
        raise ValueError('请同时提供数据库 JDBC 地址、账号和密码三项引用')
    preview = argparse.Namespace(**vars(args))
    for index, option in enumerate(BOOTSTRAP_OPTIONS):
        if not getattr(preview, option):
            setattr(preview, option, f'DRY_RUN_{index}:1')
    if not (preview.admin_password_secret or preview.admin_password_hash_secret):
        preview.admin_password_secret = 'DRY_RUN_ADMIN:1'
    substitutions(preview)


def apply_reuse(cloud, tool, args):
    result = tool['legacy_reuse'](cloud, args.region, database=not args.db_url_secret)
    for option, reference in zip(BOOTSTRAP_OPTIONS[:3], result['database']):
        setattr(args, option, reference)
    if not (args.admin_password_secret or args.admin_password_hash_secret):
        if not result['administrator']:
            raise ValueError('旧服务未从 Secret Manager 绑定管理员密码，请显式提供密码引用')
        name, reference = result['administrator']
        option = 'admin_password_secret' if name == 'ADMIN_PASSWORD' else 'admin_password_hash_secret'
        setattr(args, option, reference)
    if not any(pair.startswith('ADMIN_USERNAME=') for pair in [*args.env, *args.secret]):
        username = 'ADMIN_USERNAME=' + result['username']
        (args.secret if result['username_is_secret'] else args.env).append(username)
    tool['initialize_internal_secrets'](cloud, args)


def submit(args, values, environment=None):
    command = gcloud_command() + [
        'builds', 'submit', str(ROOT), '--project=' + args.project, '--region=' + args.region,
        '--config=' + str(ROOT / 'deploy/gcp/cloudbuild-common.yaml'),
        '--ignore-file=' + str(ROOT / 'deploy/gcp/.gcloudignore'),
        '--substitutions=' + dictionary_flag(values), '--quiet',
    ]
    if args.build_service_account:
        command.append('--service-account=projects/' + args.project + '/serviceAccounts/' + args.build_service_account)
    options = {} if environment is None else {'env': environment}
    return subprocess.run(command, cwd=ROOT, check=False, **options).returncode


def provision_and_submit(args, sources, payloads):
    with ExitStack() as resources:
        if args.promote_test:
            promotion = runpy.run_path(str(ROOT / 'deploy/gcp/promote_test.py'))
            directory = resources.enter_context(tempfile.TemporaryDirectory(prefix='release-', dir=ROOT / 'deploy/gcp'))
            path = Path(directory) / 'test-promotion.enc'
            snapshot = promotion['read_test'](args.test_ssh, database_configuration, sensitive_environment)

        tool = runpy.run_path(str(ROOT / 'deploy/gcp/provision.py'))
        cloud = tool['Cloud'](gcloud_command(), args.project, sources)
        if args.reuse_common_api:
            apply_reuse(cloud, tool, args)
        values = substitutions(args)
        bindings = load_secret_bindings(args.secret_bindings_file)
        boot_refs = {getattr(args, option) for option in BOOTSTRAP_OPTIONS}
        boot_refs.add(args.admin_password_secret or args.admin_password_hash_secret)
        if args.redis_secret:
            boot_refs.add(args.redis_secret)
        for identifier in sources.values():
            if identifier in {ref.split(':', 1)[0] for ref in boot_refs}:
                raise ValueError('上游 Secret ID 与启动凭据重叠：' + identifier)
        if not sources and not args.promote_test:
            return submit(args, values, cloud.environment)

        for name, identifier in sources.items():
            reference = cloud.provision(identifier, payloads[name])
            cloud.grant_read(reference, args.service_account)
            bindings[name] = reference
        if args.promote_test:
            for option in BOOTSTRAP_OPTIONS[:3]:
                reference = cloud.version(getattr(args, option))
                setattr(args, option, reference)
                for account in dict.fromkeys((args.service_account,
                                              args.migration_service_account or args.service_account)):
                    cloud.grant_read(reference, account)
            for option in ('admin_password_secret', 'admin_password_hash_secret', 'admin_jwt_secret',
                           'credential_master_key_secret', 'redis_secret'):
                if getattr(args, option):
                    reference = cloud.version(getattr(args, option))
                    setattr(args, option, reference)
                    cloud.grant_read(reference, args.service_account)
            promotion['stage'](snapshot, path, args, cloud, tool, bindings, assignments)
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', suffix='.json', prefix='release-bindings-',
                                         dir=ROOT / 'deploy/gcp', delete=False) as staged:
            generated = Path(staged.name)
            resources.callback(generated.unlink)
            json.dump(bindings, staged)
        args.secret_bindings_file = generated.relative_to(ROOT).as_posix()
        return submit(args, substitutions(args), cloud.environment)


def main(argv=None):
    args = parser().parse_args(argv)
    if args.inspect_common_api:
        tool = runpy.run_path(str(ROOT / 'deploy/gcp/provision.py'))
        cloud = tool['Cloud'](gcloud_command(), args.project)
        report = tool['legacy_reuse'](cloud, args.region, inventory=True)
        del report['username']
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    if args.inspect_test_promotion:
        promotion = runpy.run_path(str(ROOT / 'deploy/gcp/promote_test.py'))
        _, configs, credentials = promotion['read_test'](
            args.test_ssh, database_configuration, sensitive_environment)
        print(f'测试环境只读检查：{len(configs)} 个入库字段，{len(credentials)} 个密钥字段。')
        return 0
    sources = provision_arguments(args)
    checker = runpy.run_path(str(ROOT / 'deploy/test-server/migrate-database.py'))
    files = checker['check_migrations'](ROOT / checker['MIGRATION_PATH'])
    print('Common Java deployment: ' + args.project + '/' + args.region + '/' + args.service)
    print('SQL versions: ' + str(len(files)))
    print('Pipeline: secret provisioning -> schema and data migrations -> Cloud Run')
    if args.reuse_common_api:
        validate_reuse_arguments(args)
        if args.dry_run:
            print(json.dumps({'schema': args.db_schema, 'reuse': 'cyt-common-api', 'promote_test': args.promote_test,
                              'secret_sources': sources}, ensure_ascii=False, indent=2))
            print('Dry run complete: no build, database migration or deployment submitted.')
            return 0
    else:
        values = substitutions(args)
    if args.dry_run:
        if sources:
            values['secret_sources'] = sources
        if args.promote_test:
            values['promote_test'] = True
        print(json.dumps(values, ensure_ascii=False, indent=2))
        print('Dry run complete: no build, database migration or deployment submitted.')
        return 0
    if not args.reuse_common_api and not sources and not args.promote_test:
        return submit(args, values)

    payloads = {}
    for name in sources:
        value = os.environ.get(name, '').encode('utf-8')
        if not value.strip() or len(value) > 65_536:
            raise ValueError('发布环境未提供有效的 Secret 内容：' + name)
        payloads[name] = value
    return provision_and_submit(args, sources, payloads)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as error:
        raise SystemExit('Common deployment failed: ' + str(error)) from None
