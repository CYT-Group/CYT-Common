#!/usr/bin/env bash
set -Eeuo pipefail

# 首次安装等待 PostgreSQL 并执行 Flyway，业务数据迁移由独立流程处理。
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${1:-$(cd -- "$SCRIPT_DIR/../.." && pwd)}"

bash "$SCRIPT_DIR/redeploy.sh" "$ROOT" migrate
echo "==> Common PostgreSQL 结构初始化完成；业务数据复制需单独预检和执行"
