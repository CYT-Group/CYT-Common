#!/usr/bin/env bash
set -Eeuo pipefail

# 首次安装与日常升级共用相同的 Flyway 入口。
# prepare 仅准备编排、保留/生成 .env、启动并等待 PostgreSQL，不启动应用。
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${1:-$(cd -- "$SCRIPT_DIR/../.." && pwd)}"

bash "$SCRIPT_DIR/redeploy.sh" "$ROOT" prepare
SUDO=()
[[ "$(id -u)" == 0 ]] || SUDO=(sudo)
COMPOSE_DIR="${COMPOSE_DIR:-/opt/1panel/docker/compose/common-java}"
DB_CONTAINER_ID="$("${SUDO[@]}" docker compose -f "$COMPOSE_DIR/docker-compose.yml" \
  --env-file "$COMPOSE_DIR/.env" ps -q db)"
[[ "$DB_CONTAINER_ID" =~ ^[0-9a-f]{12,64}$ ]] || {
  echo 'ERROR: 无法确定本次 Compose 的唯一 PostgreSQL 容器，停止初始化' >&2; exit 2; }
"${SUDO[@]}" env \
  COMMON_FLYWAY_IMAGE="${COMMON_FLYWAY_IMAGE:-flyway/flyway:11.20.2-alpine}" \
  COMMON_DATABASE_CONTAINER="$DB_CONTAINER_ID" \
  bash "$SCRIPT_DIR/db-upgrade.sh" "$ROOT" --database-container "$DB_CONTAINER_ID" migrate
echo "==> Common PostgreSQL 结构初始化完成；业务数据复制需单独预检和执行"
