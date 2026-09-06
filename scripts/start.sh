#!/bin/bash
# ============================================
#  zuo-ai-agent 平台一键启动脚本
#  调用后端和前端启动脚本
# ============================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================
# 1. 停止所有服务
# ============================================
stop_all() {
    info "停止所有服务..."
    bash "$SCRIPT_DIR/start-backend.sh" stop
    bash "$SCRIPT_DIR/start-frontend.sh" stop
    info "已停止所有服务"
}

# ============================================
# 2. 状态检查
# ============================================
status() {
    echo ""
    echo "============================================"
    echo "       zuo-ai-agent 平台状态"
    echo "============================================"
    echo ""

    echo "--- 中间件 ---"
    redis-cli ping &>/dev/null && echo "PostgreSQL       ✅ 5432" || echo "PostgreSQL       ❌"
    redis-cli ping &>/dev/null && echo "Redis            ✅ 6379" || echo "Redis            ❌"
    rabbitmqctl status &>/dev/null && echo "RabbitMQ         ✅ 5672 (UI: http://localhost:15672)" || echo "RabbitMQ         ❌"

    echo ""
    echo "--- 后端服务 ---"
    bash "$SCRIPT_DIR/start-backend.sh" status

    echo ""
    echo "--- 前端服务 ---"
    bash "$SCRIPT_DIR/start-frontend.sh" status

    echo ""
    echo "============================================"
}

# ============================================
# 主入口
# ============================================
case "${1:-start}" in
    start)
        info "启动 zuo-ai-agent 平台..."
        bash "$SCRIPT_DIR/start-backend.sh" start
        bash "$SCRIPT_DIR/start-frontend.sh" start
        info "平台启动完成！"
        echo ""
        echo "============================================"
        echo "  🌐 访问入口 / 登录主页面:"
        echo "  👉 http://localhost:5173"
        echo "============================================"
        echo ""
        echo "  其他服务地址："
        echo "  - 监控前端:    http://localhost:5174"
        echo "  - 网关 API:    http://localhost:9000"
        echo ""
        ;;
    stop)
        stop_all
        ;;
    restart)
        stop_all
        sleep 3
        info "重新启动平台..."
        bash "$SCRIPT_DIR/start-backend.sh" start
        bash "$SCRIPT_DIR/start-frontend.sh" start
        info "平台重启完成！"
        echo ""
        echo "============================================"
        echo "  🌐 访问入口 / 登录主页面:"
        echo "  👉 http://localhost:5173"
        echo "============================================"
        echo ""
        echo "  其他服务地址："
        echo "  - 监控前端:    http://localhost:5174"
        echo "  - 网关 API:    http://localhost:9000"
        echo ""
        ;;
    status)
        status
        ;;
    backend)
        bash "$SCRIPT_DIR/start-backend.sh" "${2:-start}"
        ;;
    frontend)
        bash "$SCRIPT_DIR/start-frontend.sh" "${2:-start}"
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|backend|frontend}"
        echo ""
        echo "  start     - 启动全部服务（中间件 → 后端 → 前端）"
        echo "  stop      - 停止全部服务"
        echo "  restart   - 重启全部服务"
        echo "  status    - 查看服务状态"
        echo "  backend   - 仅操作后端服务 (start|stop|restart|status)"
        echo "  frontend  - 仅操作前端服务 (start|stop|restart|status)"
        echo ""
        ;;
esac
