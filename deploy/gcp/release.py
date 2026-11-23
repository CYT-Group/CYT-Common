#!/usr/bin/env python3
"""准备无流量 revision，校验构建/镜像，以 Cloud Run etag 条件更新切流。"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

DEPLOY = runpy.run_path(str(Path(__file__).with_name('deploy.py')))
dictionary_flag = DEPLOY['dictionary_flag']


BUILD_TIME = 'deploy.cyt.group/build-created-at'
BUILD_ID = 'deploy.cyt.group/build-id'


class ReleaseFailure(Exception):
    pass


def cli(command):
    return subprocess.run(['gcloud', *command], check=True, capture_output=True, text=True).stdout.strip()


def api(method, resource, token, body=None, query=None, missing_ok=False):
    url = 'https://run.googleapis.com/v2/' + resource
    if query:
        url += '?' + urlencode(query)
    payload = None if body is None else json.dumps(body).encode('utf-8')
    request = Request(url, data=payload, method=method, headers={
        'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    try:
        with urlopen(request, timeout=60) as response:
            return json.load(response)
    except HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        # 不读取或打印响应中的服务配置；冲突不会重试并覆盖另一构建。
        raise ReleaseFailure('Cloud Run HTTP ' + str(error.code)) from None


def revision_name(service, build_id):
    suffix = 'b' + hashlib.sha256(build_id.encode()).hexdigest()[:min(32, 61 - len(service))]
    return service + '-' + suffix


def not_older(service, created_at, build_id):
    previous = service.get('annotations', {})
    stamp = previous.get(BUILD_TIME)
    if stamp:
        before = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
        current = datetime.fromisoformat(created_at.replace('Z', '+00:00'))
        if before > current or before == current and previous.get(BUILD_ID) != build_id:
            raise ReleaseFailure('已存在更新或同时间的其他已发布构建，停止旧构建切流')


def wait_operation(operation, token, timeout=300):
    deadline = time.monotonic() + timeout
    while not operation.get('done'):
        if time.monotonic() >= deadline:
            raise ReleaseFailure('等待 Cloud Run 切流超时；请检查操作状态，脚本不会重复切流')
        time.sleep(2)
        operation = api('GET', operation['name'], token)
    if operation.get('error'):
        raise ReleaseFailure('Cloud Run 切流失败，状态码 ' + str(operation['error'].get('code')))


def promote(service, revision, digest_image, token, created_at, build_id):
    not_older(service, created_at, build_id)
    if service.get('reconciling'):
        raise ReleaseFailure('服务仍有其他配置更新，停止切流')
    # 服务把流量钉在具体 revision 上时，latestReadyRevision 不会随无流量 revision 前进，
    # 因此以“最新创建的 revision + 该 revision 已就绪”作为等价保证。
    if service.get('latestCreatedRevision', '').rsplit('/', 1)[-1] != revision:
        raise ReleaseFailure('目标 revision 已不是当前最新创建版本，停止切流')
    snapshot = api('GET', service['name'] + '/revisions/' + revision, token)
    ready = next((item for item in snapshot.get('conditions', []) if item.get('type') == 'Ready'), {})
    if ready.get('state') != 'CONDITION_SUCCEEDED':
        raise ReleaseFailure('目标 revision 尚未就绪，停止切流')
    containers = snapshot.get('containers', [])
    if not containers or containers[0].get('image') != digest_image:
        raise ReleaseFailure('目标 revision 的镜像 digest 与本次构建不一致')
    marker = next((item.get('value') for item in containers[0].get('env', []) if item['name'] == 'COMMON_RELEASE_BUILD_ID'), None)
    if marker != build_id:
        raise ReleaseFailure('目标 revision 的构建标识不一致')
    if not service.get('etag'):
        raise ReleaseFailure('Cloud Run 未返回 etag，拒绝无条件切流')
    annotations = dict(service.get('annotations', {}), **{BUILD_TIME: created_at, BUILD_ID: build_id})
    traffic = [{'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': revision, 'percent': 100}]
    for target in service.get('traffic', []):
        if target.get('tag'):
            target = dict(target, percent=0)
            if target.get('type') == 'TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST':
                target.update(type='TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', revision=revision)
            traffic.append(target)
    operation = api('PATCH', service['name'], token, {
        'name': service['name'], 'etag': service['etag'], 'traffic': traffic, 'annotations': annotations,
    }, query={'updateMask': 'traffic,annotations'})
    wait_operation(operation, token)


def release(args):
    if not re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', args.build_id):
        raise ReleaseFailure('无效的 Cloud Build ID')
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,47}[a-z0-9]|[a-z]', args.service):
        raise ReleaseFailure('无效的服务名')
    digest_image = args.digest_file.read_text(encoding='utf-8').strip()
    repository = args.image.rsplit(':', 1)[0]
    if not re.fullmatch(re.escape(repository) + r'@sha256:[0-9a-f]{64}', digest_image):
        raise ReleaseFailure('缺少与本次镜像仓库一致的推送 digest')
    created_at = cli(['builds', 'describe', args.build_id, '--project=' + args.project,
                      '--region=' + args.region, '--format=value(createTime)'])
    datetime.fromisoformat(created_at.replace('Z', '+00:00'))
    token = cli(['auth', 'print-access-token'])
    resource = f'projects/{args.project}/locations/{args.region}/services/{args.service}'
    existing = api('GET', resource, token, missing_ok=True)
    if existing:
        not_older(existing, created_at, args.build_id)
    command = list(args.gcloud_args)
    if command and command[0] == '--':
        command.pop(0)
    index = next((i for i, value in enumerate(command) if value.startswith('--update-env-vars=')), None)
    if index is None:
        raise ReleaseFailure('缺少受控的运行环境配置')
    encoded = command[index].split('=', 1)[1]
    delimiter, payload = encoded[1:].split('^', 1)
    environment = dict(item.split('=', 1) for item in payload.split(delimiter))
    # 生产以 MOCK_ALL=false 为统一默认；清除旧平台/资源级模拟覆盖，包含 Secret 引用。
    environment = {key: value for key, value in environment.items() if not key.endswith('_MOCK')}
    environment['COMMON_RELEASE_BUILD_ID'] = args.build_id
    command[index] = '--update-env-vars=' + dictionary_flag(environment)
    secret_index = next(i for i, value in enumerate(command) if value.startswith('--update-secrets='))
    encoded = command[secret_index].split('=', 1)[1]
    if encoded.startswith('^'):
        delimiter, encoded = encoded[1:].split('^', 1)
    else:
        delimiter = ','
    secrets = dict(item.split('=', 1) for item in encoded.split(delimiter) if item)
    bindings = DEPLOY['load_secret_bindings'](args.secret_bindings_file)
    if bindings.keys() & (secrets.keys() | environment.keys() | DEPLOY['RESERVED_ALIASES']):
        raise ReleaseFailure('Secret 绑定清单与发布参数重复')
    secrets.update(bindings)
    if bindings:
        command[secret_index] = '--update-secrets=' + dictionary_flag(secrets)
    if existing:
        existing_env = [item for container in existing.get('template', {}).get('containers', [])
                        for item in container.get('env', [])]
        mock_names = {item['name'] for item in existing_env if item['name'].endswith('_MOCK')}
        literal_secrets = {item['name'] for item in existing_env
                           if 'value' in item and (DEPLOY['sensitive_environment'](item['name'])
                               or DEPLOY['AUTH_URL'].search(item['value']))}
        removals = next((arg.split('=', 1)[1] for arg in command
                         if arg.startswith('--remove-env-vars=')), '')
        missing = literal_secrets - secrets.keys() - set(removals.split(','))
        if missing:
            raise ReleaseFailure('以下旧明文变量需要绑定 Secret Manager：' + ', '.join(sorted(missing)))
        for flag in ('--remove-env-vars=', '--remove-secrets='):
            removed = mock_names | (bindings.keys() if flag == '--remove-env-vars=' else set())
            position = next((i for i, value in enumerate(command) if value.startswith(flag)), None)
            if position is None:
                if removed:
                    command.append(flag + ','.join(sorted(removed)))
            elif removed:
                names = set(command[position][len(flag):].split(',')) | removed
                command[position] = flag + ','.join(sorted(names))
    revision = revision_name(args.service, args.build_id)
    deployment = ['run', 'deploy', args.service, '--project=' + args.project, '--region=' + args.region,
                  '--image=' + digest_image, '--revision-suffix=' + revision[len(args.service) + 1:], *command]
    if existing:
        deployment.append('--no-traffic')
    else:
        print('首次创建服务会立即接收流量；首次创建须串行执行。', flush=True)
    cli(deployment)
    service = api('GET', resource, token)
    promote(service, revision, digest_image, token, created_at, args.build_id)
    print('已按构建镜像 digest 校验并发布 revision：' + revision)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('project', 'region', 'service', 'build-id', 'image'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--digest-file', type=Path, required=True)
    parser.add_argument('--secret-bindings-file', default='')
    parser.add_argument('gcloud_args', nargs=argparse.REMAINDER)
    release(parser.parse_args(argv))
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        raise SystemExit('Cloud CLI 发布失败，退出码 ' + str(error.returncode)) from None
    except (ReleaseFailure, ValueError, OSError) as error:
        raise SystemExit('Common 发布停止：' + str(error)) from None
