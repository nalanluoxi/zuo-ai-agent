#!/bin/bash
# ============================================
#  zuo-ai-agent 生产环境一键启动脚本 (Docker)
#  包含 Ollama 模型自动初始化
# ============================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

cd "$PROJECT_DIR"

# ============================================
# 1. 检查 .env 文件
# ============================================
check_env() {
    if [ ! -f .env ]; then
        error ".env 文件不存在，请先创建"
        echo ""
        echo "创建 .env 文件示例："
        echo "  DB_PASSWORD=your_password"
        echo "  REDIS_PASSWORD=your_password"
        echo "  RABBITMQ_PASSWORD=your_password"
        echo "  DASHSCOPE_API_KEY=your_key"
        echo "  SILICONFLOW_API_KEY=your_key"
        exit 1
    fi
    info ".env 文件检查通过 ✅"
}

# ============================================
# 2. 创建数据目录
# ============================================
prepare_dirs() {
    info "准备数据目录..."
    mkdir -p data/postgres
    mkdir -p data/ollama
    chmod 777 data/postgres
    info "数据目录准备完成 ✅"
}

# ============================================
# 3. 停止旧容器
# ============================================
stop_old() {
    info "停止旧容器..."
    docker compose -f docker-compose.prod.yml --env-file .env down 2>/dev/null || true
}

# ============================================
# 4. 启动基础服务（postgres, redis, rabbitmq, ollama）
# ============================================
start_base() {
    info "启动基础服务 (postgres, redis, rabbitmq, ollama)..."
    docker compose -f docker-compose.prod.yml --env-file .env up -d postgres redis rabbitmq ollama
    
    info "等待基础服务就绪..."
    local retries=0
    while [ $retries -lt 60 ]; do
        local healthy=0
        
        # 检查 postgres
        if docker compose -f docker-compose.prod.yml --env-file .env ps postgres 2>/dev/null | grep -q "healthy"; then
            ((healthy++))
        fi
        
        # 检查 redis
        if docker compose -f docker-compose.prod.yml --env-file .env ps redis 2>/dev/null | grep -q "healthy"; then
            ((healthy++))
        fi
        
        # 检查 rabbitmq
        if docker compose -f docker-compose.prod.yml --env-file .env ps rabbitmq 2>/dev/null | grep -q "healthy"; then
            ((healthy++))
        fi
        
        # 检查 ollama
        if docker compose -f docker-compose.prod.yml --env-file .env ps ollama 2>/dev/null | grep -q "healthy"; then
            ((healthy++))
        fi
        
        if [ $healthy -eq 4 ]; then
            info "基础服务就绪 ✅"
            return 0
        fi
        
        echo -ne "\r等待服务就绪... ($retries/60) postgres: $([ $healthy -gt 0 ] && echo '✅' || echo '⏳') redis: $([ $healthy -gt 1 ] && echo '✅' || echo '⏳') rabbitmq: $([ $healthy -gt 2 ] && echo '✅' || echo '⏳') ollama: $([ $healthy -gt 3 ] && echo '✅' || echo '⏳')     "
        sleep 3
        ((retries++))
    done
    
    echo ""
    warn "部分服务未就绪，继续尝试..."
}

# ============================================
# 5. 初始化 Ollama 模型
# ============================================
init_ollama_models() {
    info "初始化 Ollama 模型..."
    
    local models=("qwen2.5:7b" "dengcao/Qwen3-Embedding-8B:F16")
    
    for model in "${models[@]}"; do
        # 检查模型是否已存在
        if docker exec zuo-ollama ollama list 2>/dev/null | grep -q "$model"; then
            info "模型 $model 已存在 ✅"
        else
            info "正在拉取模型 $model ..."
            if docker exec zuo-ollama ollama pull "$model"; then
                info "模型 $model 拉取成功 ✅"
            else
                warn "模型 $model 拉取失败 ❌"
            fi
        fi
    done
    
    echo ""
    info "当前 Ollama 模型列表："
    docker exec zuo-ollama ollama list
}

# ============================================
# 6. 启动后端服务
# ============================================
start_backend() {
    info "启动后端服务..."
    docker compose -f docker-compose.prod.yml --env-file .env up -d
    
    info "等待后端服务就绪..."
    local retries=0
    while [ $retries -lt 30 ]; do
        if curl -sf http://localhost:8123/api/health >/dev/null 2>&1; then
            info "后端服务就绪 ✅"
            return 0
        fi
        echo -ne "\r等待后端服务就绪... ($retries/30)"
        sleep 5
        ((retries++))
    done
    
    echo ""
    warn "后端服务可能未完全就绪"
}

# ============================================
# 7. 显示状态
# ============================================
show_status() {
    echo ""
    echo "============================================"
    echo "       zuo-ai-agent 平台状态"
    echo "============================================"
    echo ""
    docker compose -f docker-compose.prod.yml --env-file .env ps
    echo ""
    echo "============================================"
    echo "  访问入口:"
    echo "  👉 http://$(hostname -I | awk '{print $1}'):5173"
    echo "============================================"
    echo ""
}

# ============================================
# 主流程
# ============================================
main() {
    info "启动 zuo-ai-agent 生产环境..."
    echo ""
    
    check_env
    prepare_dirs
    stop_old
    start_base
    init_ollama_models
    start_backend
    show_status
    
    info "启动完成！"
}

main "$@"
