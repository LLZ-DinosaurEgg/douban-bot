#!/bin/bash

# 豆瓣爬虫 Docker 一键部署脚本
# 使用方法: ./deploy.sh [start|stop|restart|logs|status]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Docker 和 Docker Compose
check_dependencies() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
}

# 检查 .env 文件
check_env_file() {
    if [ ! -f .env ]; then
        print_warn ".env 文件不存在，正在从 env.example 创建..."
        if [ -f env.example ]; then
            cp env.example .env
            print_info "已创建 .env 文件，请编辑 .env 文件配置你的参数"
            print_warn "请至少配置 DOUBAN_COOKIE 和 CRAWLER_GROUPS 参数"
            exit 1
        else
            print_error "env.example 文件不存在"
            exit 1
        fi
    fi

    # 检查必要的配置
    # 使用 set -a 来自动导出变量，并正确处理引号
    set -a
    source .env
    set +a
    
    # 移除可能的引号后检查
    DOUBAN_COOKIE_CLEAN=$(echo "$DOUBAN_COOKIE" | sed -e "s/^['\"]//" -e "s/['\"]$//")
    if [ -z "$DOUBAN_COOKIE_CLEAN" ] || [ "$DOUBAN_COOKIE_CLEAN" = "your_douban_cookie_here" ]; then
        print_error "请在 .env 文件中配置 DOUBAN_COOKIE"
        exit 1
    fi

    if [ -z "$CRAWLER_GROUPS" ] || [ "$CRAWLER_GROUPS" = "" ]; then
        print_warn "CRAWLER_GROUPS 未配置，爬虫服务可能无法正常工作"
    fi
}

# 创建必要的目录
create_directories() {
    if [ ! -d "data" ]; then
        print_info "创建 data 目录..."
        mkdir -p data
    fi
}

# 启动服务
start_services() {
    print_info "正在启动服务..."
    docker-compose up -d --build
    print_info "服务启动完成！"
    print_info "Web 界面地址: http://localhost:${WEB_PORT:-8080}"
    print_info "查看日志: ./deploy.sh logs"
    print_info "查看状态: ./deploy.sh status"
}

# 停止服务
stop_services() {
    print_info "正在停止服务..."
    docker-compose down
    print_info "服务已停止"
}

# 重启服务
restart_services() {
    print_info "正在重启服务..."
    docker-compose restart
    print_info "服务已重启"
}

# 查看日志
view_logs() {
    if [ -z "$2" ]; then
        docker-compose logs -f
    else
        docker-compose logs -f "$2"
    fi
}

# 查看状态
view_status() {
    docker-compose ps
}

# 主函数
main() {
    check_dependencies

    case "${1:-start}" in
        start)
            check_env_file
            create_directories
            start_services
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        logs)
            view_logs "$@"
            ;;
        status)
            view_status
            ;;
        *)
            echo "使用方法: $0 [start|stop|restart|logs|status]"
            echo ""
            echo "命令说明:"
            echo "  start    - 启动所有服务（默认）"
            echo "  stop     - 停止所有服务"
            echo "  restart  - 重启所有服务"
            echo "  logs     - 查看日志（可指定服务名，如: logs web 或 logs crawler）"
            echo "  status   - 查看服务状态"
            exit 1
            ;;
    esac
}

main "$@"
