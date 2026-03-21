#!/bin/bash

# HotKey 快速测试脚本
# 直接运行，无需交互

API_BASE="http://localhost:8101/api"
ID=1
COUNT=100

echo "=========================================="
echo "  HotKey 功能测试"
echo "=========================================="
echo ""

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 检查服务
echo -e "${BLUE}检查服务状态...${NC}"
if curl -s "${API_BASE}/questionBank/get/vo?id=${ID}" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 应用服务正常"
else
    echo -e "${YELLOW}✗${NC} 应用服务不可用，请先启动应用"
    exit 1
fi
echo ""

# 测试1: 冷启动
echo -e "${BLUE}[测试1] 冷启动（无缓存）${NC}"
time1=$(curl -s -o /dev/null -w "%{time_total}" "${API_BASE}/questionBank/get/vo?id=${ID}" 2>/dev/null)
echo "   响应时间: ${time1}s"
echo ""

sleep 1

# 测试2: 高并发（触发热 key）
echo -e "${BLUE}[测试2] 高并发请求 (${COUNT} 个) - 触发热 key${NC}"
start=$(date +%s%N)
for i in $(seq 1 $COUNT); do
    curl -s -o /dev/null "${API_BASE}/questionBank/get/vo?id=${ID}" 2>/dev/null &
done
wait
end=$(date +%s%N)
duration=$(( (end - start) / 1000000 ))
avg_time=$(( duration / COUNT ))
echo "   总耗时: ${duration}ms"
echo "   平均响应时间: ${avg_time}ms/请求"
echo ""

sleep 2

# 测试3: 热 key 缓存
echo -e "${BLUE}[测试3] 热 key 缓存测试${NC}"
time2=$(curl -s -o /dev/null -w "%{time_total}" "${API_BASE}/questionBank/get/vo?id=${ID}" 2>/dev/null)
echo "   响应时间: ${time2}s"
echo ""

# 比较结果
echo "=========================================="
echo "测试结果分析"
echo "=========================================="
echo "冷启动响应时间: ${time1}s"
echo "热 key 缓存响应时间: ${time2}s"
echo ""

if (( $(echo "$time2 < $time1" | bc -l 2>/dev/null || echo "0") )); then
    improvement=$(echo "scale=1; ($time1 - $time2) * 100 / $time1" | bc 2>/dev/null || echo "0")
    echo -e "${GREEN}✅ 缓存生效！响应时间减少约 ${improvement}%${NC}"
else
    echo -e "${YELLOW}⚠️  响应时间变化不明显（可能需要更多请求才能触发热 key）${NC}"
fi

echo ""
echo "=========================================="
echo "下一步"
echo "=========================================="
echo "1. 访问 Dashboard 查看热 key 统计:"
echo "   http://localhost:8081"
echo ""
echo "2. 查看应用日志确认 HotKey 连接状态"
echo ""
echo "3. 可以多次运行此脚本观察效果"
echo ""
