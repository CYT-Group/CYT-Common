#!/usr/bin/env bash
set -Eeuo pipefail

# 用法：bash redeploy.sh [源码根目录] [deploy|prepare|migrate]
# prepare 等待 PostgreSQL；migrate 另执行 Flyway；deploy 再更新应用并验证。
SRC_DIR="${1:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)}"
MODE="${2:-deploy}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/1panel/docker/compose/common-java}"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"
ENV_FILE="$COMPOSE_DIR/.env"
WAIT_SECONDS="${WAIT_SECONDS:-300}"
SOURCE_COMPOSE="$SRC_DIR/deploy/test-server/docker-compose.yml"

case "$MODE" in
  deploy|prepare|migrate) ;;
  *) echo "ERROR: 发布模式必须是 deploy、prepare 或 migrate" >&2; exit 2 ;;
esac
[[ "$WAIT_SECONDS" =~ ^[1-9][0-9]*$ ]] || { echo "ERROR: WAIT_SECONDS 必须是正整数" >&2; exit 2; }
if [[ -n "${COMMON_JAVA_IMAGE:-}" && ! "$COMMON_JAVA_IMAGE" =~ ^[A-Za-z0-9][A-Za-z0-9._:/@-]*$ ]]; then
  echo "ERROR: COMMON_JAVA_IMAGE 格式无效" >&2
  exit 2
fi

SUDO=()
[[ "$(id -u)" == 0 ]] || SUDO=(sudo)
command -v docker >/dev/null 2>&1 || { echo "ERROR: 没有 docker" >&2; exit 2; }
[[ -f "$SOURCE_COMPOSE" ]] || { echo "ERROR: 找不到测试服编排文件" >&2; exit 2; }

# sudo 可能清理环境，明确传递本次发布使用的 Compose 覆盖项。
COMPOSE_OVERRIDES=()
for name in COMMON_JAVA_IMAGE APP_PORT COMPOSE_PROFILES CORS_ALLOW_ORIGINS; do
  if [[ -v "$name" ]]; then COMPOSE_OVERRIDES+=("$name=${!name}"); fi
done
compose() {
  local file="$1"
  shift
  "${SUDO[@]}" env "${COMPOSE_OVERRIDES[@]}" docker compose -f "$file" --env-file "$ENV_FILE" "$@"
}

"${SUDO[@]}" mkdir -p -- "$COMPOSE_DIR"
if ! "${SUDO[@]}" test -f "$ENV_FILE"; then
  temporary_environment="$(mktemp)"
  trap 'rm -f -- "$temporary_environment"' EXIT
  INITIAL_ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD:-}" python3 - "$temporary_environment" <<'PY_INIT_ENV'
import json
import os
from pathlib import Path
import secrets
import sys

password = os.environ.get("INITIAL_ADMIN_PASSWORD") or secrets.token_hex(12)
if any(ord(character) < 32 or ord(character) == 127 for character in password):
    raise SystemExit("ERROR: INITIAL_ADMIN_PASSWORD 不允许控制字符")
# Compose 会解析双引号内的转义和变量，双写美元符号可保留字面值。
quoted_password = json.dumps(password.replace("$", "$$"), ensure_ascii=False)
values = {
    "COMMON_JAVA_IMAGE": "192.168.1.182:5000/cyt-common-java:latest",
    "APP_PORT": "8088",
    "APP_ENV": "test",
    "POSTGRES_IMAGE": "postgres:17",
    "POSTGRES_DATABASE": "cyt_common",
    "POSTGRES_USERNAME": "cyt_gateway",
    "POSTGRES_PASSWORD": secrets.token_hex(16),
    "ADMIN_USERNAME": "admin",
    "ADMIN_PASSWORD": quoted_password,
    "ADMIN_JWT_SECRET": secrets.token_hex(32),
    "CREDENTIAL_MASTER_KEY": secrets.token_hex(32),
    "MOCK_ALL": "true",
    "JAVA_SEED_ENABLED": "false",
    "GATEWAY_DEV_MODE": "false",
}
Path(sys.argv[1]).write_text(
    "".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8", newline="\n"
)
PY_INIT_ENV
  "${SUDO[@]}" install -m 600 -- "$temporary_environment" "$ENV_FILE"
  echo "==> 已生成测试服 .env，后台密码保存在文件中"
fi

if ! "${SUDO[@]}" grep -Eq '^[[:blank:]]*(export[[:blank:]]+)?POSTGRES_PASSWORD[[:blank:]]*[:=][[:blank:]]*[^[:space:]#]' "$ENV_FILE"; then
  echo "ERROR: 缺少 POSTGRES_PASSWORD，请先配置 PostgreSQL 并核对数据迁移" >&2
  exit 2
fi
compose "$SOURCE_COMPOSE" config -q
"${SUDO[@]}" cp -- "$SOURCE_COMPOSE" "$COMPOSE_FILE"

echo "==> 启动 PostgreSQL 并等待就绪"
compose "$COMPOSE_FILE" up -d --wait --wait-timeout "$WAIT_SECONDS" db
[[ "$MODE" != prepare ]] || exit 0

database_id="$(compose "$COMPOSE_FILE" ps -q db)"
[[ "$database_id" =~ ^[0-9a-f]{12,64}$ ]] || {
  echo "ERROR: 无法确定本次 Compose 的唯一 PostgreSQL 容器" >&2; exit 2;
}
"${SUDO[@]}" env "${COMPOSE_OVERRIDES[@]}" \
  COMMON_FLYWAY_IMAGE="${COMMON_FLYWAY_IMAGE:-flyway/flyway:11.20.2-alpine}" \
  COMMON_DATABASE_CONTAINER="$database_id" \
  bash "$SRC_DIR/deploy/test-server/db-upgrade.sh" "$SRC_DIR" --database-container "$database_id" migrate
[[ "$MODE" != migrate ]] || exit 0

echo "==> 拉取并更新应用"
compose "$COMPOSE_FILE" pull app
compose "$COMPOSE_FILE" up -d --no-deps --pull never app

health_url="${HEALTH_URL:-}"
if [[ -z "$health_url" ]]; then
  binding="$(compose "$COMPOSE_FILE" port app 8189)"
  binding="${binding%%$'\n'*}"
  if [[ "$binding" =~ ^([0-9.]+):([0-9]{1,5})$ ]]; then
    address="${BASH_REMATCH[1]}" port="${BASH_REMATCH[2]}"
    [[ "$address" != 0.0.0.0 ]] || address=127.0.0.1
  elif [[ "$binding" =~ ^(\[[0-9A-Fa-f:.]+\]):([0-9]{1,5})$ ]]; then
    address="${BASH_REMATCH[1]}" port="${BASH_REMATCH[2]}"
    [[ "$address" != '[::]' ]] || address='[::1]'
  else
    echo "ERROR: 无法识别已发布的应用端口" >&2
    exit 2
  fi
  if (( 10#$port == 0 || 10#$port > 65535 )); then
    echo "ERROR: 已发布的应用端口无效" >&2
    exit 2
  fi
  health_url="http://$address:$((10#$port))/health"
fi

deadline=$(( $(date +%s) + WAIT_SECONDS ))
until body="$(curl --fail --silent --max-time 6 -- "$health_url")" && python3 -c '
import json
import sys

try:
    health = json.load(sys.stdin)
except (ValueError, UnicodeError):
    sys.exit(1)
sys.exit(not (isinstance(health, dict) and health.get("ok") is True
              and health.get("database") == "postgresql"
              and health.get("service") == "cyt-common-api-java"))
' <<< "$body"; do
  if (( $(date +%s) >= deadline )); then
    echo "ERROR: PostgreSQL 网关健康检查超时" >&2
    "${SUDO[@]}" docker logs --tail 60 cyt-common-java >&2 || true
    exit 1
  fi
  sleep 5
done
echo "==> PostgreSQL 网关健康检查通过"

public_url="${PUBLIC_BASE_URL-}"
if [[ ! -v PUBLIC_BASE_URL ]]; then
  public_url="$(compose "$COMPOSE_FILE" config --environment | python3 -c '
import sys

public_url = ""
for line in sys.stdin:
    key, _, value = line.rstrip("\r\n").partition("=")
    if key == "PUBLIC_BASE_URL":
        public_url = value
print(public_url)
')"
fi
if [[ -n "$public_url" ]]; then
  python3 "$SRC_DIR/deploy/test-server/verify-frontend.py" --base-url "$public_url" --timeout 10
fi

# 只有应用健康且公网验证成功后，才在现有 .env 中记录新镜像。
if [[ -n "${COMMON_JAVA_IMAGE:-}" ]]; then
  "${SUDO[@]}" python3 - "$ENV_FILE" "$COMMON_JAVA_IMAGE" <<'PY_RECORD_IMAGE'
import os
from pathlib import Path
import re
import stat
import sys
import tempfile

path = Path(sys.argv[1])
image = sys.argv[2].encode("ascii")
original = path.read_bytes()
updated, matches = re.subn(
    rb"^([ \t]*(?:export[ \t]+)?COMMON_JAVA_IMAGE[ \t]*[:=])[^\r\n]*",
    lambda match: match[1] + image,
    original,
    flags=re.MULTILINE,
)
if not matches:
    newline = b"\r\n" if b"\r\n" in original else b"\n"
    separator = b"" if not original or original.endswith(b"\n") else newline
    updated += separator + b"COMMON_JAVA_IMAGE=" + image + newline
if updated != original:
    old_info = path.stat()
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".env.", delete=False) as stream:
        temporary = Path(stream.name)
        try:
            stream.write(updated)
            stream.close()
            if hasattr(os, "chown"):
                os.chown(temporary, old_info.st_uid, old_info.st_gid)
            os.chmod(temporary, stat.S_IMODE(old_info.st_mode))
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)
PY_RECORD_IMAGE
fi
echo "==> 测试服发布完成"
