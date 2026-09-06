#!/bin/bash
# ============================================
#  zuo-ai-agent 后端一键启动脚本
#  启动顺序：中间件 → 后端服务
# ============================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/../logs"

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
# 启动单个服务
# ============================================
start_single_service() {
    local service_name=$1
    local port=$2
    local service_dir=$3

    info "单独重启 $service_name (:${port})..."

    # 检查并释放端口
    check_and_kill_port $port "$service_name"

    # 启动服务
    cd "$service_dir"
    nohup mvn spring-boot:run -q > "$LOG_DIR/$service_name.log" 2>&1 &
    info "$service_name PID: $!"

    # 等待服务就绪
    info "等待 $service_name 启动..."
    for i in $(seq 1 30); do
        if curl -s "http://localhost:$port/health" >/dev/null 2>&1; then
            info "$service_name 就绪 ✅"
            return 0
        fi
        if [ "$i" -eq 30 ]; then
            warn "$service_name 启动超时"
            return 1
        fi
        sleep 2
    done
}

# ============================================
# 2. 启动后端服务
# ============================================
start_backend() {
    info "启动后端服务..."

    # 先检查并释放端口
    info "检查端口占用情况..."
    check_and_kill_port 8100 "auth-service"
    check_and_kill_port 8123 "zuo-ai-agent"
    check_and_kill_port 8200 "log-monitor-service"
    check_and_kill_port 9000 "gateway-service"

    # 先编译主服务（后续子服务依赖）
    info "编译主项目..."
    cd "$SCRIPT_DIR/.."
    mvn compile -q 2>/dev/null

    # auth-service (8100)
    info "启动 auth-service (:8100)..."
    cd "$SCRIPT_DIR/../auth-service"
    nohup mvn spring-boot:run -q > "$LOG_DIR/auth-service.log" 2>&1 &
    info "auth-service PID: $!"

    # zuo-ai-agent (8123)
    info "启动 zuo-ai-agent (:8123)..."
    cd "$SCRIPT_DIR/.."
    nohup mvn spring-boot:run -q > "$LOG_DIR/zuo-ai-agent.log" 2>&1 &
    info "zuo-ai-agent PID: $!"

    # log-monitor-service (8200)
    info "启动 log-monitor-service (:8200)..."
    cd "$SCRIPT_DIR/../log-monitor-service"
    nohup mvn spring-boot:run -q > "$LOG_DIR/log-monitor-service.log" 2>&1 &
    info "log-monitor-service PID: $!"

    # gateway-service (9000)
    info "启动 gateway-service (:9000)..."
    cd "$SCRIPT_DIR/../gateway-service"
    nohup mvn spring-boot:run -q > "$LOG_DIR/gateway-service.log" 2>&1 &
    info "gateway-service PID: $!"

    # 等待服务就绪
    info "等待服务启动完成..."
    local services=("8100:auth-service" "8123:zuo-ai-agent" "8200:log-monitor-service" "9000:gateway-service")
    for svc in "${services[@]}"; do
        IFS=':' read -r port name <<< "$svc"
        for i in $(seq 1 30); do
            if curl -s "http://localhost:$port/health" >/dev/null 2>&1; then
                info "$name 就绪 ✅"
                break
            fi
            if [ "$i" -eq 30 ]; then
                warn "$name 启动超时"
            fi
            sleep 2
        done
    done
}

# ============================================
# 3. 停止后端服务
# ============================================
stop_backend() {
    info "停止后端服务..."
    pkill -f "zuo-ai-agent" 2>/dev/null || true
    pkill -f "auth-service" 2>/dev/null || true
    pkill -f "gateway-service" 2>/dev/null || true
    pkill -f "log-monitor-service" 2>/dev/null || true
    info "已停止所有后端服务"
}

# ============================================
# 4. 状态检查
# ============================================
status() {
    echo ""
    echo "============================================"
    echo "       zuo-ai-agent 后端服务状态"
    echo "============================================"
    echo ""

    echo "--- 中间件 ---"
    redis-cli ping &>/dev/null && echo "PostgreSQL       ✅ 5432" || echo "PostgreSQL       ❌"
    redis-cli ping &>/dev/null && echo "Redis            ✅ 6379" || echo "Redis            ❌"
    rabbitmqctl status &>/dev/null && echo "RabbitMQ         ✅ 5672 (UI: http://localhost:15672)" || echo "RabbitMQ         ❌"

    echo ""
    echo "--- 后端服务 ---"
    lsof -Pi :8100 -sTCP:LISTEN -t >/dev/null 2>&1 && echo "auth-service     ✅ http://localhost:8100/api/auth" || echo "auth-service     ❌"
    lsof -Pi :8123 -sTCP:LISTEN -t >/dev/null 2>&1 && echo "zuo-ai-agent     ✅ http://localhost:8123/api" || echo "zuo-ai-agent     ❌"
    lsof -Pi :8200 -sTCP:LISTEN -t >/dev/null 2>&1 && echo "log-monitor      ✅ http://localhost:8200/api/log" || echo "log-monitor      ❌"
    lsof -Pi :9000 -sTCP:LISTEN -t >/dev/null 2>&1 && echo "gateway-service  ✅ http://localhost:9000" || echo "gateway-service  ❌"

    echo ""
    echo "日志目录: $LOG_DIR/"
    echo "============================================"
}

# ============================================
# 主入口
# ============================================
case "${1:-start}" in
    start)
        start_middleware
        start_backend
        sleep 3
        status
        ;;
    stop)
        stop_backend
        ;;
    restart)
        stop_backend
        sleep 3
        start_middleware
        start_backend
        sleep 3
        status
        ;;
    restart-service)
        # 单独重启某个服务，不影响其他服务
        # 用法: ./start-backend.sh restart-service <service-name>
        service_name=$2
        if [ -z "$service_name" ]; then
            error "请指定要重启的服务名称"
            echo "用法: $0 restart-service <service-name>"
            echo "可选服务: auth-service, zuo-ai-agent, log-monitor-service, gateway-service"
            exit 1
        fi

        case "$service_name" in
            auth-service)
                start_single_service "auth-service" 8100 "$SCRIPT_DIR/../auth-service"
                ;;
            zuo-ai-agent)
                # 先编译
                info "编译主项目..."
                cd "$SCRIPT_DIR/.."
                mvn compile -q 2>/dev/null
                start_single_service "zuo-ai-agent" 8123 "$SCRIPT_DIR/.."
                ;;
            log-monitor-service)
                start_single_service "log-monitor-service" 8200 "$SCRIPT_DIR/../log-monitor-service"
                ;;
            gateway-service)
                start_single_service "gateway-service" 9000 "$SCRIPT_DIR/../gateway-service"
                ;;
            *)
                error "未知的服务名称: $service_name"
                echo "可选服务: auth-service, zuo-ai-agent, log-monitor-service, gateway-service"
                exit 1
                ;;
        esac
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|restart-service|status}"
        echo "  start                        - 启动所有后端服务"
        echo "  stop                         - 停止所有后端服务"
        echo "  restart                      - 重启所有后端服务"
        echo "  restart-service <name>       - 单独重启某个服务（不影响其他服务）"
        echo "                                 可选: auth-service, zuo-ai-agent, log-monitor-service, gateway-service"
        echo "  status                       - 查看服务状态"
        ;;
esac
