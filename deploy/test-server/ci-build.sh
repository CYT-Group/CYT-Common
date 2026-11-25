#!/usr/bin/env bash
set -Eeuo pipefail

# Common release protocol: 1
# VERIFY_ONLY 只校验版本和 SQL。SKIP_DEPLOY 除了校验，还构建并推送候选镜像。

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> 工作区: $ROOT"
# Jenkins 写入 .deploy-sha；手工从 Git 工作区运行时读取 HEAD。
if [[ -f "$ROOT/.deploy-sha" ]]; then
  revision="$(cat "$ROOT/.deploy-sha")"
  echo "==> 源码提交: ${revision:0:12}"
else
  revision="$(git rev-parse HEAD)"
  echo "==> 分支: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '(非 git)')  提交: $(git rev-parse --short HEAD 2>/dev/null || echo '-')"
fi
[[ "$revision" =~ ^[a-f0-9]{40}$ ]] || { echo "ERROR: 源码提交号无效" >&2; exit 2; }
[[ "${BUILD_NUMBER:-}" =~ ^[0-9]*$ ]] || { echo "ERROR: BUILD_NUMBER 必须是数字" >&2; exit 2; }

verify_only="${VERIFY_ONLY:-false}"
skip_deploy="${SKIP_DEPLOY:-false}"
no_push="${NO_PUSH:-false}"
if [[ "${no_push,,}" =~ ^(1|true|yes)$ && ! "${verify_only,,}" =~ ^(1|true|yes)$ &&
      ! "${skip_deploy,,}" =~ ^(1|true|yes)$ ]]; then
  echo "ERROR: NO_PUSH 需要启用 SKIP_DEPLOY 或 VERIFY_ONLY" >&2
  exit 2
fi

bash "$ROOT/deploy/test-server/db-upgrade.sh" "$ROOT" lint
if [[ "${verify_only,,}" =~ ^(1|true|yes)$ ]]; then
  echo "==> 版本及 SQL 校验通过"
  exit 0
fi

default_tag="sha-${revision:0:12}"
if [[ -n "${BUILD_NUMBER:-}" ]]; then
  default_tag="git-${revision:0:12}-$BUILD_NUMBER"
fi
export SOURCE_SHA="$revision"
export COMMON_JAVA_IMAGE="${COMMON_JAVA_IMAGE:-${REGISTRY:-192.168.1.182:5000}/cyt-common-java:${IMAGE_TAG:-$default_tag}}"

echo "==> 1/2 构建镜像并推送"
bash "$ROOT/deploy/test-server/build-on-server.sh" "$ROOT"

if [[ "${skip_deploy,,}" =~ ^(1|true|yes)$ ]]; then
  echo "==> 候选镜像：$COMMON_JAVA_IMAGE"
  exit 0
fi

echo "==> 2/2 等待数据库、执行迁移并更新应用"
bash "$ROOT/deploy/test-server/redeploy.sh" "$ROOT"
