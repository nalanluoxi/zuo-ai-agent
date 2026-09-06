#!/bin/bash
# ============================================
#  Ollama 模型初始化脚本
#  检查并拉取所需的模型
# ============================================

set -e

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"
OLLAMA_CONTAINER="${OLLAMA_CONTAINER:-zuo-ollama}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }

# 需要拉取的模型列表
MODELS=(
    "qwen2.5:7b"
    "dengcao/Qwen3-Embedding-8B:F16"
)

# 检查 Ollama 服务是否可用
check_ollama() {
    info "检查 Ollama 服务..."
    
    # 先尝试通过容器执行
    if docker exec "$OLLAMA_CONTAINER" ollama list &>/dev/null; then
        info "通过容器 $OLLAMA_CONTAINER 连接成功"
        return 0
    fi
    
    # 尝试通过 HTTP 连接
    if curl -sf "$OLLAMA_HOST/api/tags" &>/dev/null; then
        info "通过 HTTP $OLLAMA_HOST 连接成功"
        return 0
    fi
    
    warn "Ollama 服务未就绪，请确保容器已启动"
    return 1
}

# 检查模型是否存在
check_model() {
    local model=$1
    
    # 通过容器检查
    if docker exec "$OLLAMA_CONTAINER" ollama list 2>/dev/null | grep -q "$model"; then
        return 0
    fi
    
    return 1
}

# 拉取模型
pull_model() {
    local model=$1
    
    if check_model "$model"; then
        info "模型 $model 已存在 ✅"
        return 0
    fi
    
    info "正在拉取模型 $model ..."
    if docker exec "$OLLAMA_CONTAINER" ollama pull "$model"; then
        info "模型 $model 拉取成功 ✅"
        return 0
    else
        warn "模型 $model 拉取失败 ❌"
        return 1
    fi
}

# 主流程
main() {
    info "开始初始化 Ollama 模型..."
    echo ""
    
    if ! check_ollama; then
        echo ""
        echo "请先启动 Ollama 容器："
        echo "  docker compose -f docker-compose.prod.yml --env-file .env up -d ollama"
        echo ""
        echo "等待服务就绪后重新运行此脚本"
        exit 1
    fi
    
    echo ""
    echo "需要拉取的模型："
    for model in "${MODELS[@]}"; do
        echo "  - $model"
    done
    echo ""
    
    local failed=0
    for model in "${MODELS[@]}"; do
        if ! pull_model "$model"; then
            ((failed++))
        fi
    done
    
    echo ""
    if [ $failed -eq 0 ]; then
        info "所有模型初始化完成！"
        echo ""
        docker exec "$OLLAMA_CONTAINER" ollama list
    else
        warn "有 $failed 个模型拉取失败"
        exit 1
    fi
}

main "$@"
