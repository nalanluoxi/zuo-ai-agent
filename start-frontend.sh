#!/bin/bash
# ============================================
#  zuo-ai-agent 前端一键启动脚本
#  启动: web (5173) + monitor-web (5174)
# ============================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

mkdir -p "$LOG_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================
# 检查端口占用并 kill
# ============================================
check_and_kill_port() {
    local port=$1
    local service_name=$2

    # 检查端口是否被占用
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        warn "端口 $port 被占用，正在释放..."
        # 获取占用端口的进程 ID
        local pid=$(lsof -Pi :$port -sTCP:LISTEN -t)
        if [ -n "$pid" ]; then
            kill -9 $pid 2>/dev/null || true
            info "已 kill 进程 $pid (端口 $port)"
            sleep 1
        fi
    else
        info "端口 $port 可用"
    fi
}

# ============================================
# 1. 启动前端
# ============================================
start_frontend() {
    info "启动前端服务..."

    # 检查端口占用
    check_and_kill_port 5173 "web"
    check_and_kill_port 5174 "monitor-web"

    # web (主前端, 5173)
    info "启动 web (:5173)..."
    cd "$SCRIPT_DIR/web"
    nohup npm run dev > "$LOG_DIR/web-frontend.log" 2>&1 &
    info "web PID: $!"

    # monitor-web (监控前端, 5174)
    info "启动 monitor-web (:5174)..."
    cd "$SCRIPT_DIR/monitor-web"
    nohup npm run dev > "$LOG_DIR/monitor-web.log" 2>&1 &
    info "monitor-web PID: $!"

    # 等待启动完成
    info "等待前端启动..."
    sleep 3

    # 检查状态
    echo ""
    curl -s http://localhost:5173 > /dev/null 2>&1 && \
        echo "web              ✅ http://localhost:5173" || \
        warn "web 启动失败"

    curl -s http://localhost:5174 > /dev/null 2>&1 && \
        echo "monitor-web      ✅ http://localhost:5174" || \
        warn "monitor-web 启动失败"
    echo ""
}

# ============================================
# 2. 停止前端
# ============================================
stop_frontend() {
    info "停止前端服务..."
    pkill -f "vite.*web" 2>/dev/null || true
    pkill -f "npm run dev" 2>/dev/null || true
    info "已停止所有前端服务"
}

# ============================================
# 3. 状态检查
# ============================================
status() {
    echo ""
    echo "============================================"
    echo "       zuo-ai-agent 前端服务状态"
    echo "============================================"
    echo ""

    echo "--- 前端 ---"
    curl -s http://localhost:5173 > /dev/null 2>&1 && \
        echo "web              ✅ http://localhost:5173" || \
        echo "web              ❌"

    curl -s http://localhost:5174 > /dev/null 2>&1 && \
        echo "monitor-web      ✅ http://localhost:5174" || \
        echo "monitor-web      ❌"

    echo ""
    echo "日志目录: $LOG_DIR/"
    echo "============================================"
}

# ============================================
# 主入口
# ============================================
case "${1:-start}" in
    start)
        start_frontend
        ;;
    stop)
        stop_frontend
        ;;
    restart)
        stop_frontend
        sleep 2
        start_frontend
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo "  start   - 启动所有前端服务"
        echo "  stop    - 停止所有前端服务"
        echo "  restart - 重启所有前端服务"
        echo "  status  - 查看服务状态"
        ;;
esac
