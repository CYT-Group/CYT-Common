#!/usr/bin/env python3
"""为 Common 现有 Jenkins 任务配置原生 SCM、动态分支及可追溯的构建记录。"""
import argparse
import base64
from datetime import datetime
import http.cookiejar
import json
from pathlib import Path
import re
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET


REPOSITORY = 'https://github.com/wangzi-prince/CYT-Common.git'
DEFAULT_BRANCH = 'main'
SITE_URL = 'http://192.168.1.182:8088/'
JOB_NAME = 'cyt-common-deploy'
JOB_DISPLAY_NAME = '【测试环境】CYT-Common'
CREDENTIAL_ID = 'cyt-common-github'
CI_BASE_REGISTRY = '192.168.1.182:5000/cyt-common-ci-r1/'
PLUGINS = ('git', 'git-parameter', 'build-name-setter')


class Jenkins:
    def __init__(self, base, auth_file):
        self.base = base.rstrip('/')
        auth = json.loads(Path(auth_file).read_text(encoding='utf-8'))
        self.authorization = 'Basic ' + base64.b64encode((auth['user'] + ':' + auth['password']).encode()).decode()
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.crumb = {}

    def request(self, path, data=None, content_type='application/json'):
        headers = {'Authorization': self.authorization, 'Content-Type': content_type}
        if data is not None:
            if not self.crumb:
                issuer = self.json('/crumbIssuer/api/json')
                self.crumb = {issuer['crumbRequestField']: issuer['crumb']}
            headers.update(self.crumb)
        request = urllib.request.Request(self.base + path, data=data, headers=headers)
        with self.opener.open(request, timeout=60) as response:
            return response.read()

    def json(self, path):
        return json.loads(self.request(path))

    def groovy(self, script):
        body = urllib.parse.urlencode({'script': script}).encode()
        return self.request('/scriptText', body, 'application/x-www-form-urlencoded').decode()


def child(parent, name, text=None, **attributes):
    element = ET.SubElement(parent, name, attributes)
    if text is not None:
        element.text = text
    return element


def replace(parent, name, element):
    for old in parent.findall(name):
        parent.remove(old)
    parent.append(element)


def job_config(original, job=JOB_NAME):
    if job != JOB_NAME:
        raise ValueError('此入口只更新 Common 的现有发布任务：' + JOB_NAME)
    root = ET.fromstring(original)
    if root.tag != 'project':
        raise ValueError('仅支持现有 Freestyle 任务，不转换任务类型或历史')
    builders = root.find('builders')
    definitions = root.find('properties/hudson.model.ParametersDefinitionProperty/parameterDefinitions')
    if definitions is None or builders is None or len(builders) != 1 or builders[0].tag != 'hudson.tasks.Shell':
        raise ValueError('任务参数或构建步骤与预期不同，未覆盖其他配置')
    if root.find('concurrentBuild') is None:
        raise ValueError('缺少并发构建配置，未更新任务')
    for element in root.findall('.//description'):
        if not element.text:
            continue
        try:
            repaired = element.text.encode('latin-1').decode('utf-8')
        except (UnicodeEncodeError, UnicodeDecodeError):
            continue
        if any('\u4e00' <= char <= '\u9fff' for char in repaired):
            element.text = repaired
    display_name = root.find('displayName')
    if display_name is None:
        display_name = child(root, 'displayName')
    display_name.text = JOB_DISPLAY_NAME
    description = root.find('description')
    if description is None:
        description = child(root, 'description')
    description.text = (
        'Common 测试环境访问地址：' + SITE_URL + '\n'
        '原生 Git 检出并记录 Changes；BRANCH 实时读取远端分支，可按关键词筛选。\n'
        '构建独立版本镜像 → Flyway 校验及迁移 → 更新应用 → 健康检查。\n'
        '构建历史显示分支和提交，每次构建归档版本与镜像信息；与旧 Python common-api（8085）隔离。\n'
        'VERIFY_ONLY=true 仅验证版本及 SQL，不构建、不推送、不迁库、不部署。\n'
        'SKIP_DEPLOY=true 只构建推送，不迁库、不更新容器。')
    for item in list(definitions):
        if item.findtext('name') in ('BRANCH', 'VERIFY_ONLY'):
            definitions.remove(item)
    branch = ET.Element('net.uaznia.lukanus.hudson.plugins.gitparameter.GitParameterDefinition')
    fields = {
        'name': 'BRANCH', 'description': '发布分支：实时读取远端，默认 main，可输入关键词筛选',
        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, REPOSITORY + '#' + job + ':BRANCH')),
        'type': 'PT_BRANCH', 'branch': '', 'tagFilter': '*', 'branchFilter': 'origin/(.*)',
        'sortMode': 'ASCENDING_SMART', 'defaultValue': DEFAULT_BRANCH, 'selectedValue': 'DEFAULT',
        'useRepository': re.escape(REPOSITORY), 'quickFilterEnabled': 'true', 'listSize': '10',
        'requiredParameter': 'true',
    }
    for name, value in fields.items():
        child(branch, name, value)
    definitions.insert(0, branch)
    if not any(item.findtext('name') == 'SKIP_DEPLOY' for item in definitions):
        skip = child(definitions, 'hudson.model.BooleanParameterDefinition')
        child(skip, 'name', 'SKIP_DEPLOY')
        child(skip, 'description', '只构建推送，不迁库、不更新容器')
        child(skip, 'defaultValue', 'false')
    verify = child(definitions, 'hudson.model.BooleanParameterDefinition')
    child(verify, 'name', 'VERIFY_ONLY')
    child(verify, 'description', '仅验证检出版本及 SQL，不构建、不推送、不迁库、不部署')
    child(verify, 'defaultValue', 'false')
    scm = ET.Element('scm', {'class': 'hudson.plugins.git.GitSCM'})
    child(scm, 'configVersion', '2')
    remote = child(child(scm, 'userRemoteConfigs'), 'hudson.plugins.git.UserRemoteConfig')
    child(remote, 'url', REPOSITORY)
    child(remote, 'credentialsId', CREDENTIAL_ID)
    child(remote, 'refspec', '+refs/heads/*:refs/remotes/origin/*')
    spec = child(child(scm, 'branches'), 'hudson.plugins.git.BranchSpec')
    child(spec, 'name', '*/${BRANCH}')
    child(scm, 'doGenerateSubmoduleConfigurations', 'false')
    child(scm, 'submoduleCfg', **{'class': 'empty-list'})
    extensions = child(scm, 'extensions')
    first = child(extensions, 'hudson.plugins.git.extensions.impl.FirstBuildChangelog')
    child(first, 'makeChangelog', 'true')
    local = child(extensions, 'hudson.plugins.git.extensions.impl.LocalBranch')
    child(local, 'localBranch', '${BRANCH}')
    clone = child(extensions, 'hudson.plugins.git.extensions.impl.CloneOption')
    for name, value in {'shallow': 'false', 'noTags': 'true', 'reference': '', 'timeout': '10', 'honorRefspec': 'true'}.items():
        child(clone, name, value)
    replace(root, 'scm', scm)
    root.find('concurrentBuild').text = 'false'
    builders.clear()
    shell = child(builders, 'hudson.tasks.Shell')
    child(shell, 'command', (
        '#!/usr/bin/env bash\n'
        'set -Eeuo pipefail\n'
        'export BASE_REGISTRY="${BASE_REGISTRY-' + CI_BASE_REGISTRY + '}"\n'
        'bash /opt/cyt-common/deploy/jenkins-build.sh\n'))
    publishers = root.find('publishers')
    if publishers is None:
        publishers = child(root, 'publishers')
    for old in publishers.findall('hudson.tasks.ArtifactArchiver'):
        publishers.remove(old)
    archiver = child(publishers, 'hudson.tasks.ArtifactArchiver')
    for name, value in {'artifacts': '.jenkins-build/${BUILD_NUMBER}/*', 'allowEmptyArchive': 'true', 'onlyIfSuccessful': 'false', 'fingerprint': 'true', 'defaultExcludes': 'false'}.items():
        child(archiver, name, value)
    wrappers = root.find('buildWrappers')
    if wrappers is None:
        wrappers = child(root, 'buildWrappers')
    for old in wrappers.findall('org.jenkinsci.plugins.buildnamesetter.BuildNameSetter'):
        wrappers.remove(old)
    labels = child(wrappers, 'org.jenkinsci.plugins.buildnamesetter.BuildNameSetter')
    child(labels, 'template', '#${BUILD_NUMBER} · ${ENV,var="BRANCH"} · ${GIT_REVISION,length=8}')
    child(labels, 'descriptionTemplate', '分支: ${ENV,var="BRANCH"} | 提交: ${GIT_REVISION,length=12} | 仅验证: ${ENV,var="VERIFY_ONLY"} | 仅构建: ${ENV,var="SKIP_DEPLOY"}')
    child(labels, 'runAtStart', 'true')
    child(labels, 'runAtEnd', 'true')
    ET.indent(root, space='  ')
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


CREDENTIAL_SCRIPT = '''
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
def provider = SystemCredentialsProvider.getInstance()
def existing = provider.credentials.find { it.id == 'cyt-common-github' }
if (existing == null) {
  def token = new File('/opt/jenkins/github_token_common').text.trim()
  if (!token) throw new IllegalStateException('Common GitHub token file is empty')
  provider.credentials.add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
    'cyt-common-github', 'CYT-Common repository read access', 'x-access-token', token))
  provider.save()
}
println('Common Git credential ready')
'''


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:8090')
    parser.add_argument('--job', default=JOB_NAME, choices=(JOB_NAME,))
    parser.add_argument('--auth-file', help='服务器临时 JSON：user/password，不放进仓库')
    parser.add_argument('--input-xml', type=Path, help='离线生成：读取本地现有任务 XML，不连接 Jenkins')
    parser.add_argument('--backup-dir', type=Path, default=Path('/opt/jenkins/common-job-backups'))
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(argv)
    if args.input_xml:
        if args.apply:
            parser.error('--input-xml 与 --apply 不能一起使用')
        print(job_config(args.input_xml.read_bytes(), args.job).decode())
        return
    if not args.auth_file:
        parser.error('连接 Jenkins 时必须指定 --auth-file；离线生成可使用 --input-xml')
    client = Jenkins(args.url, args.auth_file)
    active = {p['shortName'] for p in client.json('/pluginManager/api/json?tree=plugins[shortName,active]')['plugins'] if p['active']}
    missing = set(PLUGINS) - active
    if missing:
        raise SystemExit('先安装并加载 Jenkins 插件：' + ', '.join(sorted(missing)))
    job_path = '/job/' + urllib.parse.quote(args.job, safe='')
    original = client.request(job_path + '/config.xml')
    updated = job_config(original, args.job)
    if not args.apply:
        print(updated.decode())
        return
    job = client.json(job_path + '/api/json?tree=builds[number,building],inQueue')
    if job.get('inQueue') or any(b['building'] for b in job.get('builds', [])):
        raise SystemExit('任务正在构建或排队，未更新配置')
    backup = args.backup_dir / (args.job + '-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.xml')
    backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with backup.open('xb') as stream:
        stream.write(original)
    backup.chmod(0o600)
    response = client.groovy(CREDENTIAL_SCRIPT)
    if 'Common Git credential ready' not in response or 'Exception' in response:
        raise SystemExit('Jenkins Git 凭据配置失败；原任务尚未变更')
    if client.request(job_path + '/config.xml') != original:
        raise SystemExit('任务配置被其他人修改，请重新检查后再应用')
    client.request(job_path + '/config.xml', updated, 'application/xml; charset=UTF-8')
    print('已更新任务：' + args.job)
    print('原配置备份：' + str(backup))


if __name__ == '__main__':
    main()
