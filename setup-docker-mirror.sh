#!/bin/bash

# Docker 镜像加速器快速配置脚本
# 使用方法: ./setup-docker-mirror.sh

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检测操作系统
detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
        CONFIG_FILE="$HOME/.docker/daemon.json"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
        CONFIG_FILE="/etc/docker/daemon.json"
    else
        print_error "不支持的操作系统: $OSTYPE"
        exit 1
    fi
}

# 备份现有配置
backup_config() {
    if [ -f "$CONFIG_FILE" ]; then
        BACKUP_FILE="${CONFIG_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
        print_info "备份现有配置到: $BACKUP_FILE"
        cp "$CONFIG_FILE" "$BACKUP_FILE"
    fi
}

# 配置镜像加速器
configure_mirrors() {
    print_info "配置 Docker 镜像加速器..."
    
    # 创建配置目录
    mkdir -p "$(dirname "$CONFIG_FILE")"
    
    # 镜像加速器列表
    MIRRORS=(
        "https://docker.m.daocloud.io"
        "https://dockerproxy.com"
        "https://docker.nju.edu.cn"
        "https://docker.mirrors.sjtug.sjtu.edu.cn"
    )
    
    # 构建 JSON 配置
    MIRRORS_JSON=$(printf '"%s",' "${MIRRORS[@]}" | sed 's/,$//')
    
    # 如果配置文件存在，读取现有配置
    if [ -f "$CONFIG_FILE" ]; then
        print_info "合并现有配置..."
        # 使用 Python 或 jq 来合并 JSON（如果可用）
        if command -v python3 &> /dev/null; then
            python3 << EOF
import json
import sys

try:
    with open('$CONFIG_FILE', 'r') as f:
        config = json.load(f)
except:
    config = {}

if 'registry-mirrors' not in config:
    config['registry-mirrors'] = []

mirrors = [$MIRRORS_JSON]
for mirror in mirrors:
    if mirror not in config['registry-mirrors']:
        config['registry-mirrors'].append(mirror)

with open('$CONFIG_FILE', 'w') as f:
    json.dump(config, f, indent=2)
EOF
        else
            print_warn "未找到 Python3，将覆盖现有配置"
            create_new_config
        fi
    else
        create_new_config
    fi
    
    print_info "配置完成！"
}

create_new_config() {
    cat > "$CONFIG_FILE" << EOF
{
  "registry-mirrors": [
    $MIRRORS_JSON
  ]
}
EOF
}

# 重启 Docker
restart_docker() {
    print_info "需要重启 Docker 服务使配置生效"
    
    if [ "$OS" == "macos" ]; then
        print_warn "请手动重启 Docker Desktop 应用"
        print_info "或者运行: osascript -e 'quit app \"Docker\"' && open -a Docker"
    elif [ "$OS" == "linux" ]; then
        print_info "重启 Docker 服务..."
        if command -v systemctl &> /dev/null; then
            sudo systemctl restart docker
            print_info "Docker 服务已重启"
        else
            print_warn "未找到 systemctl，请手动重启 Docker 服务"
        fi
    fi
}

# 验证配置
verify_config() {
    print_info "验证配置..."
    if docker info 2>/dev/null | grep -q "Registry Mirrors"; then
        print_info "镜像加速器配置成功！"
        docker info | grep -A 10 "Registry Mirrors"
    else
        print_warn "无法验证配置，请确保 Docker 正在运行"
    fi
}

# 主函数
main() {
    print_info "Docker 镜像加速器配置工具"
    echo ""
    
    detect_os
    print_info "检测到操作系统: $OS"
    print_info "配置文件路径: $CONFIG_FILE"
    echo ""
    
    read -p "是否继续配置？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "已取消"
        exit 0
    fi
    
    backup_config
    configure_mirrors
    restart_docker
    
    echo ""
    read -p "Docker 是否已重启？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        verify_config
    else
        print_info "配置已保存，重启 Docker 后生效"
    fi
    
    print_info "完成！现在可以重新尝试构建镜像"
}

main "$@"
