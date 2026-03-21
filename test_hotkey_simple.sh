#!/bin/bash

# HotKey 简单测试脚本（快速版本）

API_BASE="http://localhost:8101/api"
ID=1
COUNT=50

echo "=========================================="
echo "  HotKey 快速测试"
echo "=========================================="
echo ""

# 测试单个请求
echo "1. 测试单个请求..."
time1=$(curl -s -o /dev/null -w "%{time_total}" "${API_BASE}/questionBank/get/vo?id=${ID}")
echo "   响应时间: ${time1}s"
echo ""

# 高并发请求
echo "2. 高并发请求 (${COUNT} 个)..."
start=$(date +%s%N)
for i in $(seq 1 $COUNT); do
    curl -s -o /dev/null "${API_BASE}/questionBank/get/vo?id=${ID}" &
done
wait
end=$(date +%s%N)
duration=$(( (end - start) / 1000000 ))
echo "   总耗时: ${duration}ms"
echo "   平均: $(( duration / COUNT ))ms/请求"
echo ""

# 再次测试单个请求（应该有缓存）
echo "3. 再次测试单个请求（应该有缓存）..."
time2=$(curl -s -o /dev/null -w "%{time_total}" "${API_BASE}/questionBank/get/vo?id=${ID}")
echo "   响应时间: ${time2}s"
echo ""

# 比较
if (( $(echo "$time2 < $time1" | bc -l) )); then
    improvement=$(echo "scale=1; ($time1 - $time2) * 100 / $time1" | bc)
    echo "✅ 缓存生效！响应时间减少 ${improvement}%"
else
    echo "⚠️  响应时间变化不明显"
fi

echo ""
echo "访问 Dashboard 查看热 key: http://localhost:8081"
