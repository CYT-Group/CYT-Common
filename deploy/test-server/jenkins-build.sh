#!/usr/bin/env bash
set -Eeuo pipefail

# Jenkins 原生 SCM 检出到 WORKSPACE；构建、Changes 与 SQL 使用同一个提交。
ROOT="${WORKSPACE:?必须由 Jenkins 提供 WORKSPACE}"
cd "$ROOT"
BRANCH="${BRANCH:?请选择发布分支}"
SHA="$(git rev-parse --verify HEAD)"
git check-ref-format "refs/heads/$BRANCH" >/dev/null
[[ "${BUILD_NUMBER:-}" =~ ^[0-9]+$ ]] || { echo 'ERROR: BUILD_NUMBER 必须是数字' >&2; exit 2; }
if [[ "$SHA" != "${GIT_COMMIT:?必须由 Jenkins SCM 提供 GIT_COMMIT}" ]]; then
  echo 'ERROR: 工作区提交与 Jenkins 检出记录不一致，停止构建' >&2
  exit 3
fi
if [[ "$(git symbolic-ref --quiet --short HEAD || true)" != "$BRANCH" ]]; then
  echo 'ERROR: 工作区分支与发布参数不一致，停止构建' >&2
  exit 3
fi

# The installed bootstrap can be newer than the selected branch. Never fall
# back to a branch whose release script predates explicit database upgrades.
for required in deploy/test-server/ci-build.sh deploy/test-server/migrate-database.py \
  deploy/test-server/db-upgrade.sh CYT-Common-API/src/main/resources/application-prod.yml; do
  [[ -f "$required" ]] || {
    echo 'ERROR: 所选分支缺少新版 Common 发布入口，停止构建；请先合入发布与数据库迁移脚本' >&2
    exit 4
  }
done
if ! grep -Fxq '# Common release protocol: 1' deploy/test-server/ci-build.sh; then
  echo 'ERROR: 所选分支不支持 Common 发布协议 1，停止构建' >&2
  exit 4
fi

# 每个构建独立镜像，发版时不追随可能被后续构建覆盖的 latest。
export IMAGE_TAG="build-${BUILD_NUMBER}-${SHA:0:12}"
export COMMON_JAVA_IMAGE="${REGISTRY:-192.168.1.182:5000}/cyt-common-java:$IMAGE_TAG"
export SHA BRANCH
printf '%s\n' "$SHA" > .deploy-sha
printf '%s\n' "$BRANCH" > .deploy-branch
python3 - <<'PY'
import json
import os
from pathlib import Path
from urllib.parse import quote

branch, sha = os.environ['BRANCH'], os.environ['SHA']
enabled = lambda name: os.environ.get(name, '').lower() in ('1', 'true', 'yes')
metadata = {
    'build': os.environ['BUILD_NUMBER'],
    'branch': branch,
    'commit': sha,
    'imageTag': os.environ['IMAGE_TAG'],
    'image': os.environ['COMMON_JAVA_IMAGE'],
    'verifyOnly': enabled('VERIFY_ONLY'),
    'skipDeploy': enabled('SKIP_DEPLOY'),
    'branchUrl': 'https://github.com/wangzi-prince/CYT-Common/tree/' + quote(branch, safe=''),
    'commitUrl': 'https://github.com/wangzi-prince/CYT-Common/commit/' + sha,
}
output = Path('.jenkins-build') / metadata['build']
output.mkdir(parents=True, exist_ok=True)
output.joinpath('build-info.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
output.joinpath('version.txt').write_text(
    f"分支: {branch}\n提交: {sha}\n镜像: {metadata['image']}\n仅验证: {metadata['verifyOnly']}\n仅构建: {metadata['skipDeploy']}\n",
    encoding='utf-8')
PY

echo "==> 构建版本：$BRANCH @ ${SHA:0:12}；镜像：$COMMON_JAVA_IMAGE"
git log -1 --format='提交：%h%n作者：%an%n时间：%cI%n说明：%s'
# 阶段顺序和仅验证/仅构建开关统一由仓库入口管理，不在这里重复升级数据库。
bash deploy/test-server/ci-build.sh
