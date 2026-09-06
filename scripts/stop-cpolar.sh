#!/bin/bash
# ============================================
#  停止 cpolar 反向代理隧道
#  封装自 cpolar-proxy.sh stop
# ============================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$SCRIPT_DIR/cpolar-proxy.sh" stop "$@"
