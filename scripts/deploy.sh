#!/bin/bash
# ============================================
#  zuo-ai-agent 生产部署脚本（Docker Compose）
#  服务器只需安装 Docker + Docker Compose
# ============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

APP_HOME="/opt/zuo-ai-agent"
COMPOSE_FILE="$APP_HOME/docker-compose.prod.yml"
ENV_FILE="$APP_HOME/.env"

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
section() { echo -e "\n${BLUE}=== $1 ===${NC}"; }

# ============================================
# 检查依赖
# ============================================
check_prerequisites() {
    section "检查环境依赖"

    if ! command -v docker &>/dev/null; then
        error "Docker 未安装，请先安装: curl -fsSL https://get.docker.com | sh"
        exit 1
    fi
    info "Docker ✅ $(docker --version)"

    if ! command -v docker-compose &>/dev/null && ! docker compose version &>/dev/null; then
        error "Docker Compose 未安装"
        exit 1
    fi
    info "Docker Compose ✅"

    if [ ! -f "$ENV_FILE" ]; then
        error "环境变量文件不存在: $ENV_FILE"
        info "请复制模板并填写: cp $APP_HOME/.env.example $ENV_FILE"
        exit 1
    fi
    info ".env 文件 ✅"
}

# ============================================
# 初始化 Ollama 模型（后台异步执行）
# ============================================
init_ollama_models() {
    section "初始化 Ollama 模型（后台执行）"
    
    local ollama_container="zuo-ollama"
    local models=("qwen2.5:7b" "dengcao/Qwen3-Embedding-8B:F16")
    
    # 检查 Ollama 容器是否运行
    if ! docker ps --format '{{.Names}}' | grep -q "^${ollama_container}$"; then
        warn "Ollama 容器未运行，跳过模型初始化"
        return 0
    fi
    
    # 检查是否有需要拉取的模型
    local needs_pull=()
    for model in "${models[@]}"; do
        if ! docker exec "$ollama_container" ollama list 2>/dev/null | grep -qF "$model"; then
            needs_pull+=("$model")
        fi
    done
    
    if [ ${#needs_pull[@]} -eq 0 ]; then
        info "所有 Ollama 模型已存在 ✅"
        docker exec "$ollama_container" ollama list
        return 0
    fi
    
    # 后台拉取模型（不阻塞部署）
    info "以下模型需要拉取，将在后台执行: ${needs_pull[*]}"
    
    local pull_log="/opt/zuo-ai-agent/logs/ollama-pull-$(date +%Y%m%d-%H%M%S).log"
    mkdir -p /opt/zuo-ai-agent/logs
    
    nohup bash -c "
        for model in \"${needs_pull[@]}\"; do
            echo \"$(date '+%Y-%m-%d %H:%M:%S') - 正在拉取模型: \$model\"
            docker exec $ollama_container ollama pull \"\$model\" 2>&1
            echo \"$(date '+%Y-%m-%d %H:%M:%S') - 模型 \$model 拉取完成\"
        done
        echo \"\"
        echo \"$(date '+%Y-%m-%d %H:%M:%S') - 所有模型拉取完成\"
        docker exec $ollama_container ollama list
    " > "$pull_log" 2>&1 &
    
    info "模型拉取已在后台启动，日志: $pull_log"
    info "可通过以下命令查看进度:"
    info "  tail -f $pull_log"
}

# ============================================
# 1. 启动所有服务
# ============================================
start() {
    section "启动所有服务"
    check_prerequisites

    cd "$APP_HOME"
    docker-compose -f "$COMPOSE_FILE" pull
    docker-compose -f "$COMPOSE_FILE" up -d --remove-orphans

    info "等待服务启动..."
    sleep 5
    
    # 初始化 Ollama 模型
    init_ollama_models

    status
}

# ============================================
# 2. 停止所有服务
# ============================================
stop() {
    section "停止所有服务"

    if [ -f "$COMPOSE_FILE" ]; then
        cd "$APP_HOME"
        docker-compose -f "$COMPOSE_FILE" down
        info "所有服务已停止"
    else
        warn "docker-compose.prod.yml 不存在"
    fi
}

# ============================================
# 3. 重启服务
# ============================================
restart() {
    section "重启所有服务"
    stop
    sleep 3
    start
}

# ============================================
# 4. 更新部署（拉最新镜像）
# ============================================
update() {
    section "更新部署"
    check_prerequisites

    cd "$APP_HOME"
    docker-compose -f "$COMPOSE_FILE" pull
    docker-compose -f "$COMPOSE_FILE" up -d --remove-orphans

    # 清理悬空镜像
    docker image prune -f

    # 初始化 Ollama 模型
    init_ollama_models

    info "更新完成"
    status
}

# ============================================
# 5. 查看状态
# ============================================
status() {
    section "服务状态"

    if [ -f "$COMPOSE_FILE" ] && [ -f "$ENV_FILE" ]; then
        cd "$APP_HOME"
        docker-compose -f "$COMPOSE_FILE" ps
    else
        warn "配置文件不存在，无法查看状态"
    fi

    echo ""
    echo "--- 访问地址 ---"
    echo "主前端:      http://$(hostname -I 2>/dev/null | awk '{print $1}'):5173"
    echo "监控前端:    http://$(hostname -I 2>/dev/null | awk '{print $1}'):5174"
    echo "API 网关:    http://$(hostname -I 2>/dev/null | awk '{print $1}'):9000"
    echo ""
}

# ============================================
# 6. 查看日志
# ============================================
logs() {
    local service="${1:--f}"
    cd "$APP_HOME"
    docker-compose -f "$COMPOSE_FILE" logs "$service"
}

# ============================================
# 7. 进入容器
# ============================================
shell() {
    local service=$1
    if [ -z "$service" ]; then
        error "请指定服务名: auth-service, zuo-ai-agent, gateway-service, log-monitor-service, web, monitor-web"
        exit 1
    fi
    cd "$APP_HOME"
    docker-compose -f "$COMPOSE_FILE" exec "$service" sh
}

# ============================================
# 8. 数据库迁移（手动执行 SQL）
# ============================================
migrate() {
    section "数据库迁移"
    warn "请手动执行 src/main/resources/sql/ 下的迁移脚本"
    info "当前版本: phase1 ~ phase12"
}

# ============================================
# 主入口
# ============================================
case "${1:-help}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    update)
        update
        ;;
    status)
        status
        ;;
    logs)
        logs "$2"
        ;;
    shell)
        shell "$2"
        ;;
    migrate)
        migrate
        ;;
    help|*)
        echo "用法: $0 {start|stop|restart|update|status|logs|shell|migrate}"
        echo ""
        echo "  start      - 启动所有服务"
        echo "  stop       - 停止所有服务"
        echo "  restart    - 重启所有服务"
        echo "  update     - 拉取最新镜像并更新"
        echo "  status     - 查看服务状态"
        echo "  logs [svc] - 查看日志（默认 tail -f）"
        echo "  shell <svc>- 进入容器 shell"
        echo "  migrate    - 数据库迁移提示"
        echo ""
        echo "首次部署步骤:"
        echo "  1. cp .env.example .env  # 填写实际配置"
        echo "  2. bash deploy.sh start"
        ;;
esac
