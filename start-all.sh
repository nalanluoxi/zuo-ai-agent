#!/bin/bash
# 启动全部服务（中间件 + 后端 + 前端）
set -e
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

echo "=== 启动后端服务 ==="

# Gateway
echo "  启动 gateway-service (9000)..."
cd "$ROOT_DIR/gateway-service"
nohup mvn spring-boot:run -q > "$LOG_DIR/gateway.log" 2>&1 &

# Auth
echo "  启动 auth-service (8100)..."
cd "$ROOT_DIR/auth-service"
nohup mvn spring-boot:run -q > "$LOG_DIR/auth.log" 2>&1 &

# Main
echo "  启动 zuo-ai-agent (8123)..."
cd "$ROOT_DIR"
nohup mvn spring-boot:run -q > "$LOG_DIR/main.log" 2>&1 &

# Log Monitor
echo "  启动 log-monitor-service (8200)..."
cd "$ROOT_DIR/log-monitor-service"
nohup mvn spring-boot:run -q > "$LOG_DIR/log-monitor.log" 2>&1 &

echo ""
echo "=== 启动前端服务 ==="

# Web
echo "  启动 web (5173)..."
cd "$ROOT_DIR/web"
nohup npm run dev > "$LOG_DIR/web-frontend.log" 2>&1 &

# Monitor Web
echo "  启动 monitor-web (5174)..."
cd "$ROOT_DIR/monitor-web"
nohup npm run dev > "$LOG_DIR/monitor-frontend.log" 2>&1 &

echo ""
echo "全部服务已启动，查看日志: $LOG_DIR/"
echo "等待服务就绪..."
