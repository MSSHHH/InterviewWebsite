#!/bin/bash

# HotKey 启动脚本
# 用于启动 HotKey 相关的所有服务

echo "=========================================="
echo "  HotKey 服务启动脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ETCD_DIR="$PROJECT_ROOT/etcd/etcd-v3.5.15-darwin-arm64"
HOTKEY_DIR="$PROJECT_ROOT/hotkey"

# 检查端口是否被占用
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # 端口被占用
    else
        return 1  # 端口未被占用
    fi
}

# 启动 etcd
start_etcd() {
    echo -e "${BLUE}[1/3] 启动 etcd...${NC}"
    
    if check_port 2379; then
        echo -e "${GREEN}✓ etcd 已在运行（端口 2379）${NC}"
        return 0
    fi
    
    if [ ! -d "$ETCD_DIR" ]; then
        echo -e "${RED}✗ etcd 目录不存在: $ETCD_DIR${NC}"
        echo -e "${YELLOW}请检查 etcd 是否已正确安装${NC}"
        return 1
    fi
    
    if [ ! -f "$ETCD_DIR/etcd" ]; then
        echo -e "${RED}✗ etcd 可执行文件不存在${NC}"
        return 1
    fi
    
    cd "$ETCD_DIR"
    nohup ./etcd > etcd.log 2>&1 &
    ETCD_PID=$!
    cd - > /dev/null
    
    # 等待 etcd 启动
    sleep 3
    
    # 验证 etcd 是否启动成功
    if curl -s http://localhost:2379/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ etcd 启动成功 (PID: $ETCD_PID)${NC}"
        echo -e "  日志文件: $ETCD_DIR/etcd.log"
        return 0
    else
        echo -e "${RED}✗ etcd 启动失败，请检查日志: $ETCD_DIR/etcd.log${NC}"
        return 1
    fi
}

# 启动 HotKey Worker
start_hotkey_worker() {
    echo -e "${BLUE}[2/3] 启动 HotKey Worker...${NC}"
    
    if check_port 9527; then
        echo -e "${GREEN}✓ HotKey Worker 已在运行（端口 9527）${NC}"
        return 0
    fi
    
    # 检查 HotKey Worker jar 包是否存在
    WORKER_JAR=""
    if [ -f "$HOTKEY_DIR/hotkey-worker.jar" ]; then
        WORKER_JAR="$HOTKEY_DIR/hotkey-worker.jar"
    elif [ -f "$PROJECT_ROOT/hotkey-worker.jar" ]; then
        WORKER_JAR="$PROJECT_ROOT/hotkey-worker.jar"
    else
        echo -e "${YELLOW}⚠ HotKey Worker jar 包不存在${NC}"
        echo -e "${BLUE}请下载 HotKey Worker:${NC}"
        echo -e "  1. 访问: https://github.com/jd-platform/hotkey/releases"
        echo -e "  2. 下载 hotkey-worker.jar"
        echo -e "  3. 放置到: $HOTKEY_DIR/ 或项目根目录"
        echo ""
        read -p "是否已下载并放置 jar 包？(y/n): " answer
        if [ "$answer" != "y" ]; then
            return 1
        fi
        
        # 再次检查
        if [ -f "$HOTKEY_DIR/hotkey-worker.jar" ]; then
            WORKER_JAR="$HOTKEY_DIR/hotkey-worker.jar"
        elif [ -f "$PROJECT_ROOT/hotkey-worker.jar" ]; then
            WORKER_JAR="$PROJECT_ROOT/hotkey-worker.jar"
        else
            echo -e "${RED}✗ 仍未找到 HotKey Worker jar 包${NC}"
            return 1
        fi
    fi
    
    # 创建 hotkey 目录（如果不存在）
    mkdir -p "$HOTKEY_DIR"
    
    echo -e "${BLUE}启动 HotKey Worker: $WORKER_JAR${NC}"
    cd "$(dirname "$WORKER_JAR")"
    nohup java -jar "$(basename "$WORKER_JAR")" > hotkey-worker.log 2>&1 &
    WORKER_PID=$!
    cd - > /dev/null
    
    # 等待 Worker 启动
    sleep 5
    
    # 验证 Worker 是否启动成功
    if curl -s http://localhost:9527 > /dev/null 2>&1; then
        echo -e "${GREEN}✓ HotKey Worker 启动成功 (PID: $WORKER_PID)${NC}"
        echo -e "  日志文件: $(dirname "$WORKER_JAR")/hotkey-worker.log"
        return 0
    else
        echo -e "${YELLOW}⚠ HotKey Worker 可能正在启动中...${NC}"
        echo -e "  请稍后检查日志: $(dirname "$WORKER_JAR")/hotkey-worker.log"
        return 0
    fi
}

# 启动 HotKey Dashboard（可选）
start_hotkey_dashboard() {
    echo -e "${BLUE}[3/3] 启动 HotKey Dashboard (可选)...${NC}"
    
    if check_port 8081; then
        echo -e "${GREEN}✓ HotKey Dashboard 已在运行（端口 8081）${NC}"
        return 0
    fi
    
    # 检查 Dashboard jar 包是否存在
    DASHBOARD_JAR=""
    if [ -f "$HOTKEY_DIR/hotkey-dashboard.jar" ]; then
        DASHBOARD_JAR="$HOTKEY_DIR/hotkey-dashboard.jar"
    elif [ -f "$PROJECT_ROOT/hotkey-dashboard.jar" ]; then
        DASHBOARD_JAR="$PROJECT_ROOT/hotkey-dashboard.jar"
    else
        echo -e "${YELLOW}⚠ HotKey Dashboard jar 包不存在（可选，跳过）${NC}"
        echo -e "${BLUE}如需监控，请下载:${NC}"
        echo -e "  1. 访问: https://github.com/jd-platform/hotkey/releases"
        echo -e "  2. 下载 hotkey-dashboard.jar"
        echo -e "  3. 放置到: $HOTKEY_DIR/ 或项目根目录"
        return 0
    fi
    
    echo -e "${BLUE}启动 HotKey Dashboard: $DASHBOARD_JAR${NC}"
    cd "$(dirname "$DASHBOARD_JAR")"
    nohup java -jar "$(basename "$DASHBOARD_JAR")" > hotkey-dashboard.log 2>&1 &
    DASHBOARD_PID=$!
    cd - > /dev/null
    
    # 等待 Dashboard 启动
    sleep 5
    
    # 验证 Dashboard 是否启动成功
    if curl -s http://localhost:8081 > /dev/null 2>&1; then
        echo -e "${GREEN}✓ HotKey Dashboard 启动成功 (PID: $DASHBOARD_PID)${NC}"
        echo -e "  访问地址: http://localhost:8081"
        echo -e "  日志文件: $(dirname "$DASHBOARD_JAR")/hotkey-dashboard.log"
        return 0
    else
        echo -e "${YELLOW}⚠ HotKey Dashboard 可能正在启动中...${NC}"
        echo -e "  请稍后访问: http://localhost:8081"
        return 0
    fi
}

# 主函数
main() {
    echo "选择启动选项："
    echo "1. 启动所有 HotKey 服务 (etcd + Worker + Dashboard)"
    echo "2. 仅启动必需服务 (etcd + Worker)"
    echo "3. 仅启动 etcd"
    echo "4. 仅启动 HotKey Worker"
    echo "5. 仅启动 HotKey Dashboard"
    echo ""
    read -p "请选择 (1-5): " choice
    
    case $choice in
        1)
            start_etcd && start_hotkey_worker && start_hotkey_dashboard
            ;;
        2)
            start_etcd && start_hotkey_worker
            ;;
        3)
            start_etcd
            ;;
        4)
            start_hotkey_worker
            ;;
        5)
            start_hotkey_dashboard
            ;;
        *)
            echo -e "${RED}无效选择${NC}"
            exit 1
            ;;
    esac
    
    echo ""
    echo "=========================================="
    echo -e "${GREEN}启动完成！${NC}"
    echo "=========================================="
    echo ""
    echo "验证服务状态："
    echo "  ./check_env.sh"
    echo ""
    echo "访问 Dashboard（如果已启动）："
    echo "  http://localhost:8081"
    echo ""
}

main
