#!/bin/bash
# ============================================
#  cpolar 反向代理隧道管理脚本
#  功能：预检查 → 启动 cpolar → 获取公网地址 → 关闭
#
#  用法:
#    bash scripts/cpolar-proxy.sh start    # 预检查并启动隧道
#    bash scripts/cpolar-proxy.sh stop     # 停止隧道
#    bash scripts/cpolar-proxy.sh restart  # 重启隧道
#    bash scripts/cpolar-proxy.sh status   # 查看状态
#
#  环境变量（可选）:
#    CPOLAR_PROXY_PORT   - 隧道指向的本地端口 (默认: 80, 即 nginx)
# ============================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

# 配置
CPOLAR_LOG="$PROJECT_DIR/logs/cpolar-proxy.log"
CPOLAR_PID_FILE="$PROJECT_DIR/logs/cpolar-proxy.pid"
CPOLAR_WEB_UI="http://127.0.0.1:4040/api/tunnels"

# 隧道指向端口（默认 nginx:80，nginx 内部路由到各服务）
PROXY_PORT="${CPOLAR_PROXY_PORT:-80}"

# ============================================
# 颜色
# ============================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()   { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()  { echo -e "${RED}[ERROR]${NC} $1"; }
step()   { echo -e "${BLUE}[STEP]${NC} $1"; }

# ============================================
# 创建日志目录
# ============================================
mkdir -p "$PROJECT_DIR/logs"

# ============================================
# 1. 检查 cpolar 是否安装
# ============================================
check_cpolar() {
    if command -v cpolar &>/dev/null; then
        info "cpolar 已安装: $(command -v cpolar)"
        return 0
    fi
    error "cpolar 未安装！"
    echo ""
    echo "  安装方法："
    echo "  - macOS:  brew install cpolar  （或从官网下载安装）"
    echo "  - 官网:   https://www.cpolar.com/"
    echo ""
    return 1
}

# ============================================
# 2. 预检查服务状态
# ============================================
check_services() {
    step "检查依赖服务状态..."
    echo ""

    local all_ok=true

    # nginx 检查（必须）
    if curl -s "http://localhost:$PROXY_PORT" >/dev/null 2>&1; then
        info "✅ nginx (:${PROXY_PORT}) 运行中"
    else
        error "nginx (:${PROXY_PORT}) 未运行！"
        all_ok=false
    fi

    # 前端检查（可选，但建议运行）
    if curl -s "http://localhost:5173" >/dev/null 2>&1; then
        info "✅ 前端 (:5173) 运行中"
    else
        warn " 前端 (:5173) 未运行（建议启动）"
    fi

    # 监控前端检查
    if curl -s "http://localhost:5174" >/dev/null 2>&1; then
        info "✅ 监控前端 (:5174) 运行中"
    else
        warn " 监控前端 (:5174) 未运行（可选）"
    fi

    # 后端网关检查
    if curl -s "http://localhost:9000/health" >/dev/null 2>&1; then
        info "✅ 后端网关 (:9000) 运行中"
    else
        warn " 后端网关 (:9000) 未运行（可选）"
    fi

    echo ""

    if [ "$all_ok" = false ]; then
        error "nginx 未启动，无法启动 cpolar 代理！"
        echo ""
        echo "  请先确保 nginx 已运行："
        echo "  brew services start nginx"
        echo ""
        return 1
    fi

    info "✅ 服务预检查通过"
    return 0
}

# ============================================
# 3. 启动 cpolar 隧道
# ============================================
start_cpolar() {
    step "启动 cpolar 隧道（指向 localhost:${PROXY_PORT}）..."

    # 检查是否已在运行
    if [ -f "$CPOLAR_PID_FILE" ]; then
        local old_pid
        old_pid=$(cat "$CPOLAR_PID_FILE")
        if ps -p "$old_pid" >/dev/null 2>&1; then
            warn "cpolar 已在运行 (PID: $old_pid)"
            echo ""
            extract_urls
            echo ""
            return 0
        else
            warn "旧 PID 文件无效，清理中..."
            rm -f "$CPOLAR_PID_FILE"
        fi
    fi

    # 清理旧日志
    > "$CPOLAR_LOG"

    # 启动 cpolar，使用 -log=stdout 让日志输出到 stdout
    # 指向 nginx (port 80)，nginx 内部路由到各服务
    nohup cpolar http "$PROXY_PORT" -log=stdout > "$CPOLAR_LOG" 2>&1 &

    local pid=$!
    echo "$pid" > "$CPOLAR_PID_FILE"
    info "cpolar PID: $pid"

    # 等待隧道就绪（最多 20 秒）
    info "等待 cpolar 隧道就绪..."
    local i
    for i in $(seq 1 20); do
        if grep -q "Tunnel established" "$CPOLAR_LOG" 2>/dev/null; then
            info "✅ cpolar 隧道就绪"
            break
        fi
        if [ "$i" -eq 10 ]; then
            warn "⚠️ cpolar 启动较慢，请稍候..."
        fi
        if [ "$i" -eq 18 ]; then
            error "⚠️ cpolar 启动超时，请检查日志："
            echo "  cat $CPOLAR_LOG"
            echo ""
            return 1
        fi
        sleep 1
    done

    echo ""
    echo "============================================"
    echo "  cpolar 隧道已启动"
    echo "============================================"
    echo ""
    echo "  公网访问地址："
    extract_urls
    echo ""
    echo "  隧道指向: nginx :${PROXY_PORT}"
    echo "    /          -> 主前端 (:5173)"
    echo "    /monitor/  -> 监控前端 (:5174)"
    echo "    /api/      -> API 网关 (:9000)"
    echo ""
    echo "  日志文件: $CPOLAR_LOG"
    echo "============================================"
    echo ""

    return 0
}

# ============================================
# 提取公网地址（从日志解析）
# ============================================
extract_urls() {
    local urls
    urls=$(grep "Tunnel established at" "$CPOLAR_LOG" 2>/dev/null | \
           grep -oE 'https?://[^ "]+')
    if [ -n "$urls" ]; then
        echo "$urls" | while read -r url; do
            echo "  👉 $url"
        done
    else
        warn "未提取到公网地址，请查看日志："
        echo "  tail -20 $CPOLAR_LOG"
    fi
}

# ============================================
# 4. 停止 cpolar 隧道
# ============================================
stop_cpolar() {
    step "停止 cpolar 隧道..."

    local stopped=false

    # 方式一：通过 PID 文件停止
    if [ -f "$CPOLAR_PID_FILE" ]; then
        local pid
        pid=$(cat "$CPOLAR_PID_FILE")
        if ps -p "$pid" >/dev/null 2>&1; then
            # 先 SIGTERM，给 cpolar 优雅关闭的机会
            kill -TERM "$pid" 2>/dev/null
            info "已向 cpolar (PID: $pid) 发送终止信号"
            # 等待最多 5 秒
            for i in $(seq 1 5); do
                if ! ps -p "$pid" >/dev/null 2>&1; then
                    stopped=true
                    break
                fi
                sleep 1
            done
            # 如果还没退出，SIGKILL
            if ! $stopped && ps -p "$pid" >/dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null
                sleep 1
                stopped=true
            fi
            if $stopped; then
                info "已停止 cpolar (PID: $pid)"
            fi
        else
            warn "进程 $pid 已不存在"
        fi
        rm -f "$CPOLAR_PID_FILE"
    fi

    # 方式二：通过进程名兜底（杀子进程）
    # cpolar 启动后会派生子进程，需要一并清理
    local child_pids
    child_pids=$(pgrep -f "cpolar.*http" 2>/dev/null || true)
    if [ -n "$child_pids" ]; then
        for cpid in $child_pids; do
            if [ "$cpid" != "$$" ]; then
                kill -TERM "$cpid" 2>/dev/null || true
                info "已停止 cpolar 子进程 (PID: $cpid)"
                stopped=true
            fi
        done
    fi

    # 方式三：检查 cpolar web UI 进程（port 4040）
    local ui_pids
    ui_pids=$(lsof -ti:4040 2>/dev/null || true)
    if [ -n "$ui_pids" ]; then
        for upid in $ui_pids; do
            kill -TERM "$upid" 2>/dev/null || true
            info "已停止 cpolar Web UI (PID: $upid)"
            stopped=true
        done
    fi

    if $stopped; then
        info "✅ cpolar 隧道已停止"
    else
        warn "未找到运行中的 cpolar 进程"
    fi

    # 清理 PID 文件
    rm -f "$CPOLAR_PID_FILE"

    return 0
}

# ============================================
# 5. 查看状态
# ============================================
status_cpolar() {
    echo ""
    echo "============================================"
    echo "  cpolar 代理状态"
    echo "============================================"
    echo ""

    echo "--- cpolar 进程 ---"
    local cpolar_running=false
    if [ -f "$CPOLAR_PID_FILE" ]; then
        local pid
        pid=$(cat "$CPOLAR_PID_FILE")
        if ps -p "$pid" >/dev/null 2>&1; then
            info "✅ cpolar 运行中 (PID: $pid)"
            cpolar_running=true
        else
            warn "⚠️ PID 文件存在但进程已退出"
        fi
    else
        local pids
        pids=$(pgrep -f "cpolar" 2>/dev/null || true)
        if [ -n "$pids" ]; then
            info "✅ cpolar 运行中 (PIDs: $pids)"
            cpolar_running=true
        else
            echo "  cpolar 未运行"
        fi
    fi

    echo ""
    echo "--- 本地服务 ---"
    curl -s "http://localhost:$PROXY_PORT" >/dev/null 2>&1 && \
        echo "  nginx (:${PROXY_PORT})         ✅" || \
        echo "  nginx (:${PROXY_PORT})         ❌"
    curl -s "http://localhost:5173" >/dev/null 2>&1 && \
        echo "  前端 (:5173)              ✅" || \
        echo "  前端 (:5173)              ❌"
    curl -s "http://localhost:5174" >/dev/null 2>&1 && \
        echo "  监控前端 (:5174)           ✅" || \
        echo "  监控前端 (:5174)           ❌"
    curl -s "http://localhost:9000/health" >/dev/null 2>&1 && \
        echo "  后端网关 (:9000)            ✅" || \
        echo "  后端网关 (:9000)            ❌"

    echo ""
    echo "--- 公网地址 ---"
    if [ -f "$CPOLAR_LOG" ] && [ -s "$CPOLAR_LOG" ]; then
        extract_urls
    else
        echo "  无日志文件"
    fi

    echo ""
    echo "  日志文件: $CPOLAR_LOG"
    echo "============================================"
    echo ""
}

# ============================================
# 6. 重启
# ============================================
restart_cpolar() {
    stop_cpolar
    sleep 2
    check_services || exit 1
    start_cpolar
}

# ============================================
# 主入口
# ============================================
case "${1:-start}" in
    start)
        check_cpolar || exit 1
        check_services || exit 1
        start_cpolar
        ;;
    stop)
        stop_cpolar
        ;;
    restart)
        check_cpolar || exit 1
        restart_cpolar
        ;;
    status)
        status_cpolar
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "  start   - 预检查并启动 cpolar 隧道"
        echo "  stop    - 停止 cpolar 隧道"
        echo "  restart - 重启 cpolar 隧道"
        echo "  status  - 查看 cpolar 和服务状态"
        echo ""
        echo "环境变量："
        echo "  CPOLAR_PROXY_PORT   - 隧道指向的本地端口 (默认: 80, 即 nginx)"
        echo ""
        ;;
esac
