#!/bin/bash
# ============================================
#  zuo-ai-agent 平台一键启动脚本
#  启动顺序：中间件 → 应用服务 → 前端
# ============================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="/tmp/zuo-ai-agent"

mkdir -p "$LOG_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================
# 1. 启动中间件
# ============================================
start_middleware() {
    info "启动中间件..."

    # PostgreSQL
    if pg_isready -q 2>/dev/null; then
        info "PostgreSQL 已运行"
    else
        info "启动 PostgreSQL..."
        pg_ctl -D /opt/homebrew/var/postgresql@17 start 2>/dev/null || true
        sleep 2
    fi

    # Redis
    if redis-cli ping &>/dev/null; then
        info "Redis 已运行"
    else
        info "启动 Redis..."
        /opt/homebrew/opt/redis/bin/redis-server --daemonize yes 2>/dev/null || true
        sleep 2
    fi

    # RabbitMQ
    if rabbitmqctl status &>/dev/null; then
        info "RabbitMQ 已运行"
    else
        info "启动 RabbitMQ..."
        CONF_ENV_FILE="/opt/homebrew/etc/rabbitmq/rabbitmq-env.conf" \
            /opt/homebrew/opt/rabbitmq/sbin/rabbitmq-server -detached 2>/dev/null || true
        sleep 3
        rabbitmqctl add_user admin admin123 2>/dev/null || true
        rabbitmqctl set_user_tags admin administrator 2>/dev/null || true
        rabbitmqctl set_permissions admin ".*" ".*" ".*" 2>/dev/null || true
    fi

    info "中间件启动完成: PostgreSQL ✅ | Redis ✅ | RabbitMQ ✅"
}

# ============================================
# 2. 启动应用服务
# ============================================
start_services() {
    info "编译并启动应用服务..."

    # auth-service
    info "启动 auth-service (:8100)..."
    cd "$SCRIPT_DIR/auth-service"
    mvn compile -q 2>/dev/null
    nohup mvn spring-boot:run -q > "$LOG_DIR/auth-service.log" 2>&1 &
    info "auth-service PID: $!"

    # zuo-ai-agent
    info "启动 zuo-ai-agent (:8123)..."
    cd "$SCRIPT_DIR"
    mvn compile -q 2>/dev/null
    nohup mvn spring-boot:run -q > "$LOG_DIR/zuo-ai-agent.log" 2>&1 &
    info "zuo-ai-agent PID: $!"

    # log-monitor-service
    info "启动 log-monitor-service (:8200)..."
    cd "$SCRIPT_DIR/log-monitor-service"
    mvn compile -q 2>/dev/null
    nohup mvn spring-boot:run -q > "$LOG_DIR/log-monitor.log" 2>&1 &
    info "log-monitor-service PID: $!"

    # 等待服务就绪
    info "等待服务就绪..."
    for i in $(seq 1 30); do
        if curl -s http://localhost:8123/api/health | grep -q ok 2>/dev/null; then
            info "zuo-ai-agent 就绪 ✅"
            break
        fi
        sleep 2
    done
}

# ============================================
# 3. 启动前端
# ============================================
start_frontend() {
    info "启动前端 Vue3 (:5173)..."
    cd "$SCRIPT_DIR/web"
    nohup npx vite --host 0.0.0.0 > "$LOG_DIR/frontend.log" 2>&1 &
    info "前端 PID: $!"
}

# ============================================
# 4. 停止所有服务
# ============================================
stop_all() {
    info "停止所有服务..."
    pkill -f "zuo-ai-agent" 2>/dev/null || true
    pkill -f "auth-service" 2>/dev/null || true
    pkill -f "log-monitor" 2>/dev/null || true
    pkill -f "vite" 2>/dev/null || true
    info "已停止所有服务"
}

# ============================================
# 5. 状态检查
# ============================================
status() {
    echo ""
    echo "============================================"
    echo "           zuo-ai-agent 平台状态"
    echo "============================================"
    echo ""

    echo "--- 中间件 ---"
    redis-cli ping &>/dev/null && echo "Redis            ✅ 6379" || echo "Redis            ❌"
    rabbitmqctl status &>/dev/null && echo "RabbitMQ         ✅ 5672 (UI: http://localhost:15672)" || echo "RabbitMQ         ❌"
    pg_isready -q && echo "PostgreSQL       ✅ 5432" || echo "PostgreSQL       ❌"

    echo ""
    echo "--- 应用服务 ---"
    curl -s http://localhost:8100/api/auth/swagger-ui.html > /dev/null 2>&1 && echo "auth-service     ✅ http://localhost:8100/api/auth" || echo "auth-service     ❌"
    curl -s http://localhost:8123/api/health | grep -q ok 2>/dev/null && echo "zuo-ai-agent     ✅ http://localhost:8123/api" || echo "zuo-ai-agent     ❌"
    curl -s http://localhost:8200/api/log/collect -X POST -H "Content-Type: application/json" -d '{}' 2>/dev/null | grep -q ok && echo "log-monitor      ✅ http://localhost:8200/api/log" || echo "log-monitor      ❌"

    echo ""
    echo "--- 前端 ---"
    curl -s http://localhost:5173 > /dev/null 2>&1 && echo "Vue3 前端        ✅ http://localhost:5173" || echo "Vue3 前端        ❌"

    echo ""
    echo "============================================"
    echo " 日志文件: $LOG_DIR/"
    echo "============================================"
}

# ============================================
# 主入口
# ============================================
case "${1:-start}" in
    start)
        start_middleware
        start_services
        start_frontend
        sleep 5
        status
        ;;
    stop)
        stop_all
        ;;
    restart)
        stop_all
        sleep 3
        start_middleware
        start_services
        start_frontend
        sleep 5
        status
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo "  start   - 启动全部服务（中间件 → 应用 → 前端）"
        echo "  stop    - 停止全部服务"
        echo "  restart - 重启全部服务"
        echo "  status  - 查看服务状态"
        ;;
esac