#!/bin/bash

# HotKey 功能测试脚本
# 用于测试热 key 探测和缓存功能

echo "=========================================="
echo "  HotKey 功能测试脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
API_BASE="http://localhost:8101/api"
QUESTION_BANK_ID=1
CONCURRENT_REQUESTS=100
TEST_ROUNDS=3

# 检查服务是否运行
check_service() {
    local service_name=$1
    local check_command=$2
    
    if eval "$check_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $service_name 运行中"
        return 0
    else
        echo -e "${RED}✗${NC} $service_name 未运行"
        return 1
    fi
}

# 测试单个请求
test_single_request() {
    local id=$1
    local round=$2
    local start_time=$(date +%s%N)
    
    response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
        "${API_BASE}/questionBank/get/vo?id=${id}" \
        2>/dev/null)
    
    local end_time=$(date +%s%N)
    local http_code=$(echo "$response" | tail -2 | head -1)
    local time_total=$(echo "$response" | tail -1)
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✓${NC} 请求成功 - 响应时间: ${time_total}s"
        return 0
    else
        echo -e "${RED}✗${NC} 请求失败 - HTTP状态码: $http_code"
        return 1
    fi
}

# 并发测试
concurrent_test() {
    local id=$1
    local count=$2
    local round=$3
    
    echo -e "${BLUE}第 ${round} 轮测试：并发 ${count} 个请求...${NC}"
    
    local success_count=0
    local fail_count=0
    local total_time=0
    local times=()
    
    local start_time=$(date +%s%N)
    
    # 并发发送请求
    for i in $(seq 1 $count); do
        (
            request_start=$(date +%s%N)
            http_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${API_BASE}/questionBank/get/vo?id=${id}" 2>/dev/null)
            request_end=$(date +%s%N)
            request_time=$(( (request_end - request_start) / 1000000 ))  # 转换为毫秒
            
            if [ "$http_code" = "200" ]; then
                echo "SUCCESS:$request_time"
            else
                echo "FAIL:$http_code"
            fi
        ) &
    done
    
    # 等待所有请求完成
    wait
    
    local end_time=$(date +%s%N)
    local total_duration=$(( (end_time - start_time) / 1000000 ))
    
    echo -e "${GREEN}✓${NC} 所有请求完成，总耗时: ${total_duration}ms"
    echo ""
}

# 测试缓存效果
test_cache_effect() {
    local id=$1
    
    echo -e "${BLUE}测试缓存效果...${NC}"
    echo ""
    
    # 第一轮：冷启动（无缓存）
    echo -e "${YELLOW}[1] 冷启动测试（无缓存）${NC}"
    local start1=$(date +%s%N)
    curl -s -o /dev/null -w "响应时间: %{time_total}s\n" \
        "${API_BASE}/questionBank/get/vo?id=${id}"
    local end1=$(date +%s%N)
    local time1=$(( (end1 - start1) / 1000000 ))
    echo ""
    
    sleep 1
    
    # 第二轮：可能有缓存
    echo -e "${YELLOW}[2] 第二次请求（可能有缓存）${NC}"
    local start2=$(date +%s%N)
    curl -s -o /dev/null -w "响应时间: %{time_total}s\n" \
        "${API_BASE}/questionBank/get/vo?id=${id}"
    local end2=$(date +%s%N)
    local time2=$(( (end2 - start2) / 1000000 ))
    echo ""
    
    # 比较响应时间
    if [ $time2 -lt $time1 ]; then
        local improvement=$(( (time1 - time2) * 100 / time1 ))
        echo -e "${GREEN}✓${NC} 缓存生效！响应时间减少 ${improvement}%"
        echo -e "   第一次: ${time1}ms → 第二次: ${time2}ms"
    else
        echo -e "${YELLOW}⚠${NC} 响应时间变化不明显"
        echo -e "   第一次: ${time1}ms → 第二次: ${time2}ms"
    fi
    echo ""
}

# 高并发测试（触发热 key）
trigger_hotkey() {
    local id=$1
    local count=$2
    
    echo -e "${BLUE}高并发测试（触发热 key 探测）...${NC}"
    echo -e "并发请求数: ${count}"
    echo ""
    
    local start_time=$(date +%s%N)
    local success=0
    local fail=0
    
    # 并发发送请求
    for i in $(seq 1 $count); do
        (
            http_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${API_BASE}/questionBank/get/vo?id=${id}" 2>/dev/null)
            if [ "$http_code" = "200" ]; then
                echo "SUCCESS"
            else
                echo "FAIL:$http_code"
            fi
        ) &
    done
    
    # 等待所有请求完成
    wait
    
    local end_time=$(date +%s%N)
    local duration=$(( (end_time - start_time) / 1000000 ))
    
    echo -e "${GREEN}✓${NC} 高并发测试完成，总耗时: ${duration}ms"
    echo -e "   平均每个请求: $(( duration / count ))ms"
    echo ""
    echo -e "${YELLOW}提示:${NC} 访问 Dashboard (http://localhost:8081) 查看热 key 统计"
    echo ""
}

# 检查 Dashboard
check_dashboard() {
    echo -e "${BLUE}检查 HotKey Dashboard...${NC}"
    
    if curl -s http://localhost:8081 > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Dashboard 可访问: http://localhost:8081"
        echo -e "   请在浏览器中打开查看热 key 统计"
    else
        echo -e "${YELLOW}⚠${NC} Dashboard 不可访问"
    fi
    echo ""
}

# 主函数
main() {
    echo "检查服务状态..."
    check_service "etcd" "curl -s http://localhost:2379/health"
    check_service "应用服务" "curl -s ${API_BASE}/questionBank/get/vo?id=1 > /dev/null"
    echo ""
    
    read -p "请输入要测试的题库 ID (默认: ${QUESTION_BANK_ID}): " input_id
    QUESTION_BANK_ID=${input_id:-$QUESTION_BANK_ID}
    
    read -p "请输入并发请求数 (默认: ${CONCURRENT_REQUESTS}): " input_count
    CONCURRENT_REQUESTS=${input_count:-$CONCURRENT_REQUESTS}
    
    echo ""
    echo "=========================================="
    echo "开始测试..."
    echo "=========================================="
    echo ""
    
    # 1. 测试缓存效果
    test_cache_effect $QUESTION_BANK_ID
    
    # 2. 高并发测试
    trigger_hotkey $QUESTION_BANK_ID $CONCURRENT_REQUESTS
    
    # 3. 再次测试缓存效果（应该更快）
    echo -e "${BLUE}测试热 key 缓存效果...${NC}"
    test_cache_effect $QUESTION_BANK_ID
    
    # 4. 检查 Dashboard
    check_dashboard
    
    echo "=========================================="
    echo -e "${GREEN}测试完成！${NC}"
    echo "=========================================="
    echo ""
    echo "测试结果分析："
    echo "1. 如果响应时间明显减少，说明 HotKey 缓存生效"
    echo "2. 访问 Dashboard 查看热 key 统计: http://localhost:8081"
    echo "3. 查看应用日志确认 HotKey 客户端连接状态"
    echo ""
}

# 运行主函数
main
