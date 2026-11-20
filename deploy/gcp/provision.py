"""Resolve legacy Common bindings and provision release secrets without logging payloads."""

from base64 import urlsafe_b64decode
from hmac import compare_digest
import json
import os
import re
import secrets
import subprocess
from urllib.parse import parse_qs, unquote, urlsplit


LEGACY_SERVICE = 'cyt-common-api'
LEGACY_INSTANCE = 'cyt-db-primary'
PORTAL_DATABASE = ('PORTAL_OA_JDBC_URL:1', 'PORTAL_OA_DB_USERNAME:1', 'PORTAL_OA_DB_PASSWORD:1')
REF = re.compile(r'[A-Za-z0-9_-]{1,255}:(?:latest|[1-9][0-9]*)\Z')


class Cloud:
    def __init__(self, command, project, excluded_environment=()):
        self.command = command
        self.project = project
        self.environment = {key: value for key, value in os.environ.items()
                            if key not in excluded_environment}

    def call(self, *args, payload=None, optional=False):
        try:
            result = subprocess.run([*self.command, *args, '--project=' + self.project, '--quiet'],
                                    input=payload, capture_output=True, env=self.environment, timeout=60)
        except subprocess.TimeoutExpired:
            raise ValueError('Google Cloud 操作超时：' + ' '.join(args[:3])) from None
        if not result.returncode:
            return result.stdout
        detail = result.stderr.decode('utf-8', 'replace').lower()
        absent = ('not found' in detail or 'cannot find service' in detail) and not any(
            marker in detail for marker in ('permission', 'denied', 'unauthorized', 'reauthentication'))
        if optional and absent:
            return None
        raise ValueError('Google Cloud 操作失败：' + ' '.join(args[:3]) + '。请检查 CLI 登录与资源权限')

    def service(self, name, region, *, optional=False):
        result = self.call('run', 'services', 'describe', name, '--region=' + region, '--format=json',
                           optional=optional)
        return json.loads(result) if result is not None else None

    def version(self, reference, *, optional=False):
        if not REF.fullmatch(reference):
            raise ValueError('Secret 引用必须为同项目的 Secret ID:版本')
        name, version = reference.split(':', 1)
        result = self.call('secrets', 'versions', 'describe', version, '--secret=' + name,
                           '--format=json', optional=optional)
        if result is None:
            return None
        data = json.loads(result)
        number = data.get('name', '').rsplit('/', 1)[-1]
        if data.get('state') != 'ENABLED' or not number.isdigit():
            raise ValueError('Secret 没有可用的已启用版本：' + name)
        return name + ':' + number

    def value(self, reference):
        name, version = reference.split(':', 1)
        encoded = self.call('secrets', 'versions', 'access', version, '--secret=' + name,
                            '--format=get(payload.data)').strip()
        return urlsafe_b64decode(encoded + b'=' * (-len(encoded) % 4))

    def provision(self, name, value, *, generated=False):
        existing = self.call('secrets', 'describe', name, '--format=value(name)', optional=True)
        if existing is None:
            self.call('secrets', 'create', name, '--replication-policy=automatic', '--data-file=-',
                      payload=value)
            return self.version(name + ':1')
        current = self.version(name + (':1' if generated else ':latest'), optional=True)
        if current and (generated or compare_digest(self.value(current), value)):
            return current
        if generated:
            raise ValueError('初始化 Secret 已存在且缺少版本 1，请显式指定要使用的固定版本：' + name)
        version_name = self.call('secrets', 'versions', 'add', name, '--data-file=-',
                                 '--format=value(name)', payload=value).decode('utf-8').strip()
        number = version_name.rsplit('/', 1)[-1]
        if not number.isdigit():
            raise ValueError('Secret Manager 未返回新版本号：' + name)
        return name + ':' + number

    def grant_read(self, reference, account):
        self.call('secrets', 'add-iam-policy-binding', reference.split(':', 1)[0],
                  '--member=serviceAccount:' + account, '--role=roles/secretmanager.secretAccessor',
                  '--condition=None', '--format=none')


def service_settings(service):
    containers = service['spec']['template']['spec']['containers']
    container = next((entry for entry in containers if entry.get('ports')), containers[0])
    return {item['name']: item for item in container.get('env', [])}


def binding(settings, name):
    ref = settings.get(name, {}).get('valueFrom', {}).get('secretKeyRef')
    return ref['name'] + ':' + ref['key'] if ref else None


def verify_database_reuse(cloud, legacy, settings, region):
    connection = f'{cloud.project}:{region}:{LEGACY_INSTANCE}'
    instances = legacy['spec']['template']['metadata'].get('annotations', {}).get(
        'run.googleapis.com/cloudsql-instances', '').split(',')
    source = binding(settings, 'DATABASE_URL')
    if cloud.project != 'cyt-digital' or connection not in instances or not source:
        raise ValueError('旧服务的 PostgreSQL 实例绑定未匹配，请显式指定数据库三项 Secret')
    references = tuple(cloud.version(ref) for ref in PORTAL_DATABASE)
    try:
        old = urlsplit(cloud.value(cloud.version(source)).decode('utf-8'))
        jdbc = cloud.value(references[0]).decode('utf-8')
        target = urlsplit(jdbc.removeprefix('jdbc:'))
        query = parse_qs(old.query)
        username = unquote(old.username) if old.username else query.get('user', [''])[0]
        password = unquote(old.password) if old.password else query.get('password', [''])[0]
    except (UnicodeError, ValueError):
        raise ValueError('无法验证旧服务与 JDBC 连接的兼容性') from None
    instance = json.loads(cloud.call('sql', 'instances', 'describe', LEGACY_INSTANCE, '--format=json'))
    private_ips = {entry['ipAddress'] for entry in instance.get('ipAddresses', [])
                   if entry.get('type') == 'PRIVATE'}
    if not (
        jdbc.startswith('jdbc:postgresql://')
        and old.scheme.split('+', 1)[0] == 'postgresql'
        and old.path == target.path == '/cyt'
        and (old.hostname in private_ips or '/cloudsql/' + connection in query.get('host', []))
        and target.hostname in private_ips
        and instance.get('region') == region
        and instance.get('databaseVersion', '').startswith('POSTGRES_')
        and username and password
        and compare_digest(username.encode(), cloud.value(references[1]))
        and compare_digest(password.encode(), cloud.value(references[2]))
    ):
        raise ValueError('Portal JDBC 连接与旧 Common PostgreSQL 账号或目标不一致，请显式指定数据库三项 Secret')
    return references


def legacy_reuse(cloud, region, *, database=True, inventory=False):
    legacy = cloud.service(LEGACY_SERVICE, region)
    settings = service_settings(legacy)
    password_hash = binding(settings, 'ADMIN_PASSWORD_HASH')
    password = binding(settings, 'ADMIN_PASSWORD')
    admin = None
    if password_hash:
        ref = cloud.version(password_hash)
        if not re.fullmatch(rb'[0-9a-fA-F]{64}', cloud.value(ref)):
            raise ValueError('旧管理员摘要与 Common 的 SHA-256 登录格式不兼容')
        admin = ('ADMIN_PASSWORD_HASH', ref)
    elif password:
        admin = ('ADMIN_PASSWORD', cloud.version(password))
    username = settings.get('ADMIN_USERNAME', {}).get('value', 'admin')
    username_ref = binding(settings, 'ADMIN_USERNAME')
    if not username_ref and not re.fullmatch(r'[^\s=]{1,64}', username):
        raise ValueError('旧管理员账号格式无效，请显式配置 ADMIN_USERNAME')
    return {
        'database': verify_database_reuse(cloud, legacy, settings, region) if database else (),
        'administrator': admin,
        'username': cloud.version(username_ref) if username_ref else username,
        'username_is_secret': bool(username_ref),
        'optional': {name: cloud.version(ref) for name in (
            'REDIS_URL', 'WEWORK_SECRET', 'WEWORK_PROXY_TOKEN', 'WEWORK_CORP_ID',
            'WEWORK_AGENT_ID', 'WEWORK_PROXY_BASE', 'CREDENTIAL_MASTER_KEY', 'ADMIN_JWT_SECRET',
            'ADMIN_TOKEN') if inventory and (ref := binding(settings, name))},
    }


def service_secret_id(service, name):
    return service.replace('-', '_').upper() + '_' + name


def initialize_internal_secrets(cloud, args):
    target = cloud.service(args.service, args.region, optional=True)
    existing = service_settings(target) if target else {}
    for option, name in (('admin_jwt_secret', 'ADMIN_JWT_SECRET'),
                         ('credential_master_key_secret', 'CREDENTIAL_MASTER_KEY')):
        if getattr(args, option):
            continue
        prior = binding(existing, name)
        if prior:
            reference = cloud.version(prior)
        elif name in existing:
            raise ValueError('现有服务使用明文 ' + name + '，请先提交同值的 Secret Manager 引用')
        else:
            reference = cloud.provision(service_secret_id(args.service, name),
                                        secrets.token_urlsafe(48).encode(), generated=True)
            cloud.grant_read(reference, args.service_account)
        setattr(args, option, reference)
