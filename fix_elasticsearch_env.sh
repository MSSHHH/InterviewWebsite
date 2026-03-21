#!/bin/bash

# 修复 Elasticsearch Java 环境配置

echo "=========================================="
echo "  修复 Elasticsearch Java 环境"
echo "=========================================="
echo ""

ES_ENV_FILE="/opt/homebrew/etc/elasticsearch/elasticsearch-env"
ES_BIN="/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch"
JAVA_11_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"

# 检查 Java 11
if [ ! -f "$JAVA_11_HOME/bin/java" ]; then
    echo "错误: Java 11 未找到，请先安装: brew install openjdk@11"
    exit 1
fi

echo "1. 检查当前配置..."
if [ -f "$ES_ENV_FILE" ]; then
    echo "   当前配置:"
    cat "$ES_ENV_FILE"
else
    echo "   配置文件不存在，将创建"
fi

echo ""
echo "2. 更新配置..."
# 备份
if [ -f "$ES_ENV_FILE" ]; then
    cp "$ES_ENV_FILE" "${ES_ENV_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
fi

# 写入配置
cat > "$ES_ENV_FILE" << EOF
export ES_JAVA_HOME="$JAVA_11_HOME"
EOF

echo "   ✓ 配置已更新: ES_JAVA_HOME=$JAVA_11_HOME"

echo ""
echo "3. 修改启动脚本以加载环境配置..."

# 检查启动脚本
if [ -f "$ES_BIN" ]; then
    # 备份
    cp "$ES_BIN" "${ES_BIN}.backup.$(date +%Y%m%d_%H%M%S)"
    
    # 创建新的启动脚本
    cat > "$ES_BIN" << 'SCRIPT'
#!/bin/bash
# 加载环境配置
if [ -f /opt/homebrew/etc/elasticsearch/elasticsearch-env ]; then
    source /opt/homebrew/etc/elasticsearch/elasticsearch-env
fi
# 执行 Elasticsearch
exec "/opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch" "$@"
SCRIPT
    
    chmod +x "$ES_BIN"
    echo "   ✓ 启动脚本已更新"
else
    echo "   ✗ 启动脚本不存在"
fi

echo ""
echo "4. 测试配置..."
export ES_JAVA_HOME="$JAVA_11_HOME"
if "$JAVA_11_HOME/bin/java" -version > /dev/null 2>&1; then
    echo "   ✓ Java 11 可用"
else
    echo "   ✗ Java 11 不可用"
    exit 1
fi

echo ""
echo "5. 重启服务..."
brew services stop elasticsearch-full
sleep 2
brew services start elasticsearch-full

echo ""
echo "等待服务启动..."
sleep 5

if curl -s http://localhost:9200 > /dev/null 2>&1; then
    echo "✓ Elasticsearch 启动成功！"
    echo ""
    echo "测试连接:"
    curl -s http://localhost:9200 | head -5
else
    echo "⚠ Elasticsearch 可能还在启动中，请查看日志:"
    echo "  tail -f /opt/homebrew/var/log/elasticsearch.log"
fi

echo ""
echo "=========================================="
echo "修复完成！"
echo "=========================================="
