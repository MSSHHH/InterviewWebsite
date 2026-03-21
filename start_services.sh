#!/bin/bash

# 服务启动脚本
# 用于快速启动项目所需的服务

echo "=========================================="
echo "  面试鸭项目服务启动脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查命令是否存在
check_command() {
    if command -v $1 &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# 启动 MySQL
start_mysql() {
    echo -e "${BLUE}启动 MySQL...${NC}"
    if check_command "brew"; then
        brew services start mysql 2>/dev/null || echo -e "${YELLOW}请手动启动 MySQL: brew services start mysql${NC}"
    elif check_command "systemctl"; then
        sudo systemctl start mysql 2>/dev/null || echo -e "${YELLOW}请手动启动 MySQL: sudo systemctl start mysql${NC}"
    else
        echo -e "${YELLOW}请手动启动 MySQL 服务${NC}"
    fi
    sleep 2
}

# 启动 Redis
start_redis() {
    echo -e "${BLUE}启动 Redis...${NC}"
    if check_command "brew"; then
        brew services start redis 2>/dev/null || echo -e "${YELLOW}请手动启动 Redis: brew services start redis${NC}"
    elif check_command "systemctl"; then
        sudo systemctl start redis 2>/dev/null || echo -e "${YELLOW}请手动启动 Redis: sudo systemctl start redis${NC}"
    else
        echo -e "${YELLOW}请手动启动 Redis 服务${NC}"
    fi
    sleep 2
}

# 启动 etcd
start_etcd() {
    echo -e "${BLUE}启动 etcd (HotKey 需要)...${NC}"
    ETCD_DIR="etcd/etcd-v3.5.15-darwin-arm64"
    if [ -d "$ETCD_DIR" ]; then
        cd "$ETCD_DIR"
        if [ -f "./etcd" ]; then
            nohup ./etcd > etcd.log 2>&1 &
            echo -e "${GREEN}etcd 已启动（日志: $ETCD_DIR/etcd.log）${NC}"
            cd - > /dev/null
        else
            echo -e "${RED}etcd 可执行文件不存在${NC}"
        fi
    else
        echo -e "${YELLOW}etcd 目录不存在，请检查安装${NC}"
    fi
    sleep 2
}

# 启动 Elasticsearch
start_elasticsearch() {
    echo -e "${BLUE}启动 Elasticsearch (可选)...${NC}"
    if check_command "brew"; then
        brew services start elasticsearch 2>/dev/null || echo -e "${YELLOW}Elasticsearch 未安装，跳过${NC}"
    else
        echo -e "${YELLOW}请手动启动 Elasticsearch${NC}"
    fi
    sleep 2
}

# 主函数
main() {
    echo "选择要启动的服务："
    echo "1. 启动必需服务 (MySQL + Redis)"
    echo "2. 启动 HotKey 相关服务 (etcd)"
    echo "3. 启动所有服务 (MySQL + Redis + etcd + Elasticsearch)"
    echo "4. 仅启动 MySQL"
    echo "5. 仅启动 Redis"
    echo "6. 仅启动 etcd"
    echo ""
    read -p "请选择 (1-6): " choice
    
    case $choice in
        1)
            start_mysql
            start_redis
            ;;
        2)
            start_etcd
            ;;
        3)
            start_mysql
            start_redis
            start_etcd
            start_elasticsearch
            ;;
        4)
            start_mysql
            ;;
        5)
            start_redis
            ;;
        6)
            start_etcd
            ;;
        *)
            echo -e "${RED}无效选择${NC}"
            exit 1
            ;;
    esac
    
    echo ""
    echo -e "${GREEN}启动完成！${NC}"
    echo ""
    echo "运行 ./check_env.sh 检查服务状态"
}

main
