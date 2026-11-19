#!/usr/bin/env python3
"""Google CLI 发布 Common：构建同源 JAR 镜像 → Flyway Job → Cloud Run。"""
import argparse
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
SECRET_REF = re.compile(r'[A-Za-z0-9_-]{1,255}:(?:latest|[1-9][0-9]*)\Z')
ACCOUNT = re.compile(r'[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\.gserviceaccount\.com\Z')
ENV_NAME = re.compile(r'[A-Z][A-Z0-9_]*\Z')
SENSITIVE_NAME = re.compile(r'(?:^|_)(?:PASSWORD|PASSWORD_HASH|SECRET|TOKEN|API_KEY|ACCESS_KEY|PRIVATE_KEY|MASTER_KEY|CREDENTIALS|SA_JSON)$')
RESERVED_ALIASES = {
    'PORT', 'POSTGRES_URL', 'POSTGRES_USERNAME', 'POSTGRES_PASSWORD',
    'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_APPLICATION_JSON',
    'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'REDIS_URL',
    'COMMON_RELEASE_BUILD_ID',
}
STALE_ALIASES = RESERVED_ALIASES - {'PORT', 'REDIS_URL', 'COMMON_RELEASE_BUILD_ID'}


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
        if kind == 'env' and SENSITIVE_NAME.search(key):
            raise ValueError('敏感变量请使用 --secret 引用 Secret Manager：' + key)
        if kind == 'env' and key.endswith('_MOCK') and value.lower() in ('1', 'true', 'yes', 'on'):
            raise ValueError('生产发布不能开启平台模拟：' + key)
        values[key] = value
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
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,23}[a-z0-9]|[a-z]', args.vpc_connector):
        raise ValueError('请提供同区域 VPC 连接器的短名称')
    for account in (args.service_account, args.migration_service_account or args.service_account,
                    args.build_service_account or args.service_account):
        if not ACCOUNT.fullmatch(account):
            raise ValueError('服务账号应为 gserviceaccount.com 邮箱')
    references = [args.db_url_secret, args.db_user_secret, args.db_password_secret,
                  args.admin_password_secret, args.admin_jwt_secret, args.credential_master_key_secret]
    if args.redis_secret:
        references.append(args.redis_secret)
    if any(not SECRET_REF.fullmatch(value) for value in references):
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
        'JAVA_SEED_ENABLED': 'false', 'ADMIN_DEV_LOGIN': 'false', 'CUSTOM_API_ALLOW_LOOPBACK': 'false',
        'DISCORD_GATEWAY_ENABLED': 'true' if args.discord_listener else 'false',
    }
    secrets = {
        'SPRING_DATASOURCE_URL': args.db_url_secret, 'SPRING_DATASOURCE_USERNAME': args.db_user_secret,
        'SPRING_DATASOURCE_PASSWORD': args.db_password_secret, 'ADMIN_PASSWORD': args.admin_password_secret,
        'ADMIN_JWT_SECRET': args.admin_jwt_secret, 'CREDENTIAL_MASTER_KEY': args.credential_master_key_secret,
    }
    if args.redis_secret:
        secrets['REDIS_URL'] = args.redis_secret
    extra_env, extra_secrets = assignments(args.env, 'env'), assignments(args.secret, 'secret')
    managed = set(environment) | set(secrets) | RESERVED_ALIASES
    if managed & (set(extra_env) | set(extra_secrets)) or set(extra_env) & set(extra_secrets):
        raise ValueError('额外参数不能覆盖部署入口管理的生产、数据库或运行配置')
    environment.update(extra_env)
    secrets.update(extra_secrets)
    values = {
        '_SERVICE': args.service, '_REGION': args.region, '_REGISTRY': registry,
        '_JOB_PREFIX': job_prefix, '_VPC_CONNECTOR': args.vpc_connector, '_VPC_EGRESS': args.vpc_egress,
        '_RUNTIME_SA': args.service_account,
        '_MIGRATION_SA': args.migration_service_account or args.service_account,
        '_MIGRATION_SECRETS': dictionary_flag({'FLYWAY_URL': args.db_url_secret,
                                              'FLYWAY_USER': args.db_user_secret,
                                              'FLYWAY_PASSWORD': args.db_password_secret}),
        '_RUNTIME_SECRETS': dictionary_flag(secrets), '_RUNTIME_ENV': dictionary_flag(environment),
        '_AUTH_FLAG': '--allow-unauthenticated' if args.public else '--no-allow-unauthenticated',
        '_MIN_INSTANCES': '1' if args.discord_listener else '0', '_MAX_INSTANCES': str(args.max_instances),
        '_CPU_FLAG': '--no-cpu-throttling' if args.discord_listener else '--cpu-throttling',
        '_REMOVE_ENV_VARS': ','.join(sorted(STALE_ALIASES | set(secrets))),
        '_REMOVE_SECRET_VARS': ','.join(sorted(STALE_ALIASES)),
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
    result.add_argument('--service-account', required=True)
    result.add_argument('--migration-service-account')
    result.add_argument('--build-service-account')
    result.add_argument('--vpc-connector', default='cyt-vpc-connector')
    result.add_argument('--vpc-egress', choices=('all-traffic', 'private-ranges-only'), default='all-traffic')
    result.add_argument('--registry', help='默认 gcr.io/项目；可指定已有 Artifact Registry 仓库')
    result.add_argument('--migration-job-prefix')
    result.add_argument('--db-url-secret', required=True, help='PostgreSQL JDBC URL Secret 名称:版本')
    result.add_argument('--db-user-secret', required=True)
    result.add_argument('--db-password-secret', required=True)
    result.add_argument('--admin-password-secret', required=True)
    result.add_argument('--admin-jwt-secret', required=True)
    result.add_argument('--credential-master-key-secret', required=True)
    result.add_argument('--redis-secret', help='可选；多实例时必须显式配置')
    result.add_argument('--max-instances', type=int, default=1)
    result.add_argument('--discord-listener', action='store_true', help='启用后台监听，并配置常驻实例及持续 CPU')
    result.add_argument('--env', action='append', default=[], metavar='NAME=VALUE')
    result.add_argument('--secret', action='append', default=[], metavar='ENV=NAME:VERSION')
    result.add_argument('--public', action='store_true', help='允许浏览器到达服务，网关与管理端仍执行应用认证')
    result.add_argument('--dry-run', action='store_true', help='仅校验本地 SQL 并输出发布计划，不调用云端')
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    values = substitutions(args)
    checker = runpy.run_path(str(ROOT / 'deploy/test-server/migrate-database.py'))
    files = checker['check_migrations'](ROOT / checker['MIGRATION_PATH'])
    print('Common Java deployment: ' + args.project + '/' + args.region + '/' + args.service)
    print('SQL versions: ' + str(len(files)))
    print('Pipeline: build same-origin JAR + SQL images -> push -> Flyway Job (wait) -> Cloud Run')
    if args.dry_run:
        print(json.dumps(values, ensure_ascii=False, indent=2))
        print('Dry run complete: no build, database migration or deployment submitted.')
        return 0
    command = gcloud_command() + [
        'builds', 'submit', str(ROOT), '--project=' + args.project, '--region=' + args.region,
        '--config=' + str(ROOT / 'deploy/gcp/cloudbuild-common.yaml'),
        '--ignore-file=' + str(ROOT / 'deploy/gcp/.gcloudignore'),
        '--substitutions=' + dictionary_flag(values), '--quiet',
    ]
    if args.build_service_account:
        command.append('--service-account=projects/' + args.project + '/serviceAccounts/' + args.build_service_account)
    return subprocess.run(command, cwd=ROOT, check=False).returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as error:
        raise SystemExit('Common deployment failed: ' + str(error)) from None
