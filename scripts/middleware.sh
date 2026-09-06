#!/bin/bash
# ============================================
#  zuo-ai-agent 中间件管理脚本
#  管理: PostgreSQL + Redis + RabbitMQ
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

APP_HOME="/opt/zuo-ai-agent"
DATA_DIR="$APP_HOME/data"

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
section() { echo -e "\n${BLUE}=== $1 ===${NC}"; }

# ============================================
# 检查命令是否存在
# ============================================
check_command() {
    local cmd=$1
    local install_hint=$2
    
    if ! command -v "$cmd" &> /dev/null; then
        error "命令未找到: $cmd"
        info "安装提示: $install_hint"
        return 1
    fi
    return 0
}

# ============================================
# 检查端口占用
# ============================================
check_port() {
    local port=$1
    local service=$2
    
    if ss -tlnp 2>/dev/null | grep -q ":$port " || \
       netstat -tlnp 2>/dev/null | grep -q ":$port "; then
        return 0  # 端口被占用（服务运行中）
    fi
    return 1  # 端口空闲（服务未运行）
}

# ============================================
# 1. 检查中间件状态
# ============================================
status() {
    section "中间件状态检查"
    echo ""
    
    echo "--- PostgreSQL (5432) ---"
    if check_command psql "apt install postgresql-client"; then
        if check_port 5432 "postgresql"; then
            if pg_isready -q 2>/dev/null; then
                echo -e "状态: ${GREEN}运行中 ✅${NC}"
            else
                echo -e "状态: ${RED}端口占用但连接失败 ⚠️${NC}"
            fi
        else
            echo -e "状态: ${RED}未运行 ❌${NC}"
        fi
    fi
    
    echo ""
    echo "--- Redis (6379) ---"
    if check_command redis-cli "apt install redis-tools"; then
        if check_port 6379 "redis"; then
            if redis-cli ping &>/dev/null; then
                echo -e "状态: ${GREEN}运行中 ✅${NC}"
            else
                echo -e "状态: ${RED}端口占用但连接失败 ⚠️${NC}"
            fi
        else
            echo -e "状态: ${RED}未运行 ❌${NC}"
        fi
    fi
    
    echo ""
    echo "--- RabbitMQ (5672 / 15672) ---"
    if check_command rabbitmqctl "apt install rabbitmq-server"; then
        if check_port 5672 "rabbitmq"; then
            if rabbitmqctl status &>/dev/null; then
                echo -e "状态: ${GREEN}运行中 ✅${NC}"
                echo "管理界面: http://localhost:15672"
            else
                echo -e "状态: ${RED}端口占用但连接失败 ⚠️${NC}"
            fi
        else
            echo -e "状态: ${RED}未运行 ❌${NC}"
        fi
    fi
    
    echo ""
}

# ============================================
# 2. 启动所有中间件
# ============================================
start() {
    section "启动中间件"
    
    mkdir -p "$DATA_DIR"
    
    # PostgreSQL
    if ! check_port 5432 "postgresql"; then
        info "启动 PostgreSQL..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl start postgresql
        else
            sudo -u postgres pg_ctl -D /var/lib/postgresql/data start 2>/dev/null || \
            pg_ctl -D "$DATA_DIR/postgresql" start 2>/dev/null || \
            error "PostgreSQL 启动失败"
        fi
        sleep 2
        if pg_isready -q 2>/dev/null; then
            info "PostgreSQL 启动成功 ✅"
        else
            error "PostgreSQL 启动失败 ❌"
        fi
    else
        info "PostgreSQL 已在运行"
    fi
    
    # Redis
    if ! check_port 6379 "redis"; then
        info "启动 Redis..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl start redis
        else
            redis-server --daemonize yes --dir "$DATA_DIR/redis" 2>/dev/null || \
            error "Redis 启动失败"
        fi
        sleep 1
        if redis-cli ping &>/dev/null; then
            info "Redis 启动成功 ✅"
        else
            error "Redis 启动失败 ❌"
        fi
    else
        info "Redis 已在运行"
    fi
    
    # RabbitMQ
    if ! check_port 5672 "rabbitmq"; then
        info "启动 RabbitMQ..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl start rabbitmq-server
        else
            rabbitmq-server -detached 2>/dev/null || \
            error "RabbitMQ 启动失败"
        fi
        sleep 3
        if rabbitmqctl status &>/dev/null; then
            info "RabbitMQ 启动成功 ✅"
            # 初始化默认用户
            init_rabbitmq
        else
            error "RabbitMQ 启动失败 ❌"
        fi
    else
        info "RabbitMQ 已在运行"
    fi
    
    echo ""
}

# ============================================
# 3. 停止所有中间件
# ============================================
stop() {
    section "停止中间件"
    
    # PostgreSQL
    if check_port 5432 "postgresql"; then
        info "停止 PostgreSQL..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl stop postgresql
        else
            sudo -u postgres pg_ctl -D /var/lib/postgresql/data stop 2>/dev/null || true
        fi
        info "PostgreSQL 已停止"
    fi
    
    # Redis
    if check_port 6379 "redis"; then
        info "停止 Redis..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl stop redis
        else
            redis-cli shutdown 2>/dev/null || true
        fi
        info "Redis 已停止"
    fi
    
    # RabbitMQ
    if check_port 5672 "rabbitmq"; then
        info "停止 RabbitMQ..."
        if command -v systemctl &>/dev/null; then
            sudo systemctl stop rabbitmq-server
        else
            rabbitmqctl stop 2>/dev/null || true
        fi
        info "RabbitMQ 已停止"
    fi
    
    echo ""
}

# ============================================
# 4. 初始化 RabbitMQ 用户
# ============================================
init_rabbitmq() {
    info "初始化 RabbitMQ 用户..."
    
    # 创建 admin 用户（如果不存在）
    rabbitmqctl add_user admin admin123 2>/dev/null || true
    rabbitmqctl set_user_tags admin administrator 2>/dev/null || true
    rabbitmqctl set_permissions -p / admin ".*" ".*" ".*" 2>/dev/null || true
    
    info "RabbitMQ 用户初始化完成 (admin/admin123)"
}

# ============================================
# 5. 初始化 PostgreSQL 数据库
# ============================================
init_db() {
    section "初始化 PostgreSQL 数据库"
    
    local db_name="${1:-zuo_ai_agent}"
    local db_user="${2:-zuo_ai}"
    local db_pass="${3:-zuo_ai_2024}"
    
    if ! check_command psql "apt install postgresql-client"; then
        return 1
    fi
    
    info "创建数据库用户: $db_user"
    sudo -u postgres psql -c "CREATE USER $db_user WITH PASSWORD '$db_pass';" 2>/dev/null || \
        warn "用户可能已存在"
    
    info "创建数据库: $db_name"
    sudo -u postgres psql -c "CREATE DATABASE $db_name OWNER $db_user;" 2>/dev/null || \
        warn "数据库可能已存在"
    
    info "授权"
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $db_name TO $db_user;" 2>/dev/null
    
    info "数据库初始化完成"
    echo "  数据库: $db_name"
    echo "  用户: $db_user"
    echo "  密码: $db_pass"
    echo ""
}

# ============================================
# 6. 备份数据
# ============================================
backup() {
    section "备份中间件数据"
    
    local backup_dir="$DATA_DIR/backups/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$backup_dir"
    
    # 备份 PostgreSQL
    info "备份 PostgreSQL..."
    if check_command pg_dump "apt install postgresql-client"; then
        pg_dump -U zuo_ai -h localhost zuo_ai_agent > "$backup_dir/postgres.sql" 2>/dev/null || \
            warn "PostgreSQL 备份失败"
    fi
    
    # 备份 Redis（RDB 文件）
    info "备份 Redis..."
    local redis_dir="$DATA_DIR/redis"
    if [ -f "$redis_dir/dump.rdb" ]; then
        cp "$redis_dir/dump.rdb" "$backup_dir/redis.rdb"
        info "Redis 备份完成"
    else
        warn "Redis dump 文件不存在"
    fi
    
    info "备份完成: $backup_dir"
    ls -lh "$backup_dir"
    echo ""
}

# ============================================
# 主入口
# ============================================
case "${1:-status}" in
    start)
        start
        status
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        sleep 2
        start
        status
        ;;
    status)
        status
        ;;
    init-db)
        init_db "$2" "$3" "$4"
        ;;
    init-rabbitmq)
        init_rabbitmq
        ;;
    backup)
        backup
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|init-db|init-rabbitmq|backup}"
        echo ""
        echo "  start            - 启动所有中间件"
        echo "  stop             - 停止所有中间件"
        echo "  restart          - 重启所有中间件"
        echo "  status           - 查看中间件状态"
        echo "  init-db [name] [user] [pass] - 初始化数据库（默认: zuo_ai_agent / zuo_ai / zuo_ai_2024）"
        echo "  init-rabbitmq    - 初始化 RabbitMQ 用户"
        echo "  backup           - 备份数据"
        echo ""
        ;;
esac
