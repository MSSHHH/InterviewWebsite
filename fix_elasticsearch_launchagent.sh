#!/bin/bash

# 修复 Elasticsearch LaunchAgent 配置
# 解决后台启动时找不到 Java 的问题

echo "=========================================="
echo "  修复 Elasticsearch LaunchAgent 配置"
echo "=========================================="
echo ""

PLIST_FILE="$HOME/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist"
ES_ENV_FILE="/opt/homebrew/etc/elasticsearch/elasticsearch-env"
JAVA_11_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"

# 检查 Java 11
if [ ! -f "$JAVA_11_HOME/bin/java" ]; then
    echo "错误: Java 11 未找到，请先安装: brew install openjdk@11"
    exit 1
fi

echo "1. 检查 LaunchAgent 配置..."
if [ -f "$PLIST_FILE" ]; then
    echo "   ✓ LaunchAgent 文件存在"
else
    echo "   ✗ LaunchAgent 文件不存在"
    exit 1
fi

echo ""
echo "2. 备份 LaunchAgent 配置..."
cp "$PLIST_FILE" "${PLIST_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
echo "   ✓ 已备份"

echo ""
echo "3. 更新 LaunchAgent 配置..."

# 创建包装脚本
WRAPPER_SCRIPT="/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh"
cat > "$WRAPPER_SCRIPT" << 'WRAPPER'
#!/bin/bash
# Elasticsearch 启动包装脚本
# 加载环境配置
if [ -f /opt/homebrew/etc/elasticsearch/elasticsearch-env ]; then
    source /opt/homebrew/etc/elasticsearch/elasticsearch-env
fi
# 执行 Elasticsearch
exec "/opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch" "$@"
WRAPPER

chmod +x "$WRAPPER_SCRIPT"
echo "   ✓ 包装脚本已创建: $WRAPPER_SCRIPT"

# 更新 plist 文件
cat > "$PLIST_FILE" << PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>homebrew.mxcl.elasticsearch-full</string>
	<key>LimitLoadToSessionType</key>
	<array>
		<string>Aqua</string>
		<string>Background</string>
		<string>LoginWindow</string>
		<string>StandardIO</string>
		<string>System</string>
	</array>
	<key>ProgramArguments</key>
	<array>
		<string>$WRAPPER_SCRIPT</string>
	</array>
	<key>EnvironmentVariables</key>
	<dict>
		<key>ES_JAVA_HOME</key>
		<string>$JAVA_11_HOME</string>
	</dict>
	<key>RunAtLoad</key>
	<true/>
	<key>StandardErrorPath</key>
	<string>/opt/homebrew/var/log/elasticsearch.log</string>
	<key>StandardOutPath</key>
	<string>/opt/homebrew/var/log/elasticsearch.log</string>
	<key>WorkingDirectory</key>
	<string>/opt/homebrew/var</string>
</dict>
</plist>
PLIST

echo "   ✓ LaunchAgent 配置已更新"

echo ""
echo "4. 卸载并重新加载 LaunchAgent..."
launchctl unload "$PLIST_FILE" 2>/dev/null
sleep 1
launchctl load "$PLIST_FILE" 2>/dev/null

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
    echo ""
    echo "如果仍有问题，可以尝试手动启动:"
    echo "  export ES_JAVA_HOME=\"$JAVA_11_HOME\""
    echo "  /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch"
fi

echo ""
echo "=========================================="
echo "修复完成！"
echo "=========================================="
