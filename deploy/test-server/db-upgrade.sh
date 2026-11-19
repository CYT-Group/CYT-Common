#!/usr/bin/env bash
set -Eeuo pipefail

# 日常增量结构升级；不复制旧 MySQL/Python 业务数据。
ROOT="${1:?用法: db-upgrade.sh <源码根目录> [migrate|info|validate|lint]}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
shift
python3 "$SCRIPT_DIR/migrate-database.py" --root "$ROOT" "$@"
