#!/bin/bash

# 最终修复方案：直接修改 elasticsearch-env 脚本

echo "=========================================="
echo "  Elasticsearch 最终修复方案"
echo "=========================================="
echo ""

ES_ENV_SCRIPT="/opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch-env"
ES_ENV_CONFIG="/opt/homebrew/etc/elasticsearch/elasticsearch-env"
JAVA_11_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"

# 检查 Java 11
if [ ! -f "$JAVA_11_HOME/bin/java" ]; then
    echo "错误: Java 11 未找到，请先安装: brew install openjdk@11"
    exit 1
fi

echo "1. 备份原始脚本..."
if [ -f "$ES_ENV_SCRIPT" ]; then
    cp "$ES_ENV_SCRIPT" "${ES_ENV_SCRIPT}.backup.$(date +%Y%m%d_%H%M%S)"
    echo "   ✓ 已备份: ${ES_ENV_SCRIPT}.backup.*"
fi

echo ""
echo "2. 修改 elasticsearch-env 脚本..."

# 读取原脚本，修改 Java 路径检查逻辑
# 在检查捆绑 JDK 之前，先检查 ES_JAVA_HOME 配置
sed -i.backup 's|if \[ "\$\(uname -s\)" = "Darwin" \]; then|# 优先使用配置的 ES_JAVA_HOME\nif [ ! -z "$ES_JAVA_HOME" ] && [ -f "$ES_JAVA_HOME/bin/java" ]; then\n  JAVA="$ES_JAVA_HOME/bin/java"\n  JAVA_TYPE="ES_JAVA_HOME"\nelif [ "$(uname -s)" = "Darwin" ]; then|' "$ES_ENV_SCRIPT" 2>/dev/null || {
    echo "   ⚠ sed 修改失败，使用替代方案..."
    
    # 创建新的 elasticsearch-env 包装
    cat > "$ES_ENV_CONFIG" << 'ENV'
# Elasticsearch 环境配置
# 优先使用配置的 ES_JAVA_HOME
if [ -z "$ES_JAVA_HOME" ]; then
    export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
fi

# 如果配置的路径不存在，尝试其他路径
if [ ! -f "$ES_JAVA_HOME/bin/java" ]; then
    # 尝试系统 Java
    if command -v java &> /dev/null; then
        JAVA_HOME_SYS=$(/usr/libexec/java_home 2>/dev/null || dirname $(dirname $(readlink -f $(which java))))
        if [ -f "$JAVA_HOME_SYS/bin/java" ]; then
            export ES_JAVA_HOME="$JAVA_HOME_SYS"
        fi
    fi
fi
ENV
    echo "   ✓ 环境配置文件已创建"
}

echo ""
echo "3. 更新包装脚本..."
cat > /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh << 'WRAPPER'
#!/bin/bash
# Elasticsearch 启动包装脚本

# 加载环境配置
if [ -f /opt/homebrew/etc/elasticsearch/elasticsearch-env ]; then
    source /opt/homebrew/etc/elasticsearch/elasticsearch-env
fi

# 确保 ES_JAVA_HOME 已设置
if [ -z "$ES_JAVA_HOME" ]; then
    export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
fi

# 验证 Java 可用
if [ ! -f "$ES_JAVA_HOME/bin/java" ]; then
    echo "错误: 找不到 Java，ES_JAVA_HOME=$ES_JAVA_HOME" >&2
    exit 1
fi

# 执行 Elasticsearch
exec "/opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch" "$@"
WRAPPER

chmod +x /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh
echo "   ✓ 包装脚本已更新"

echo ""
echo "4. 更新 LaunchAgent..."
cat > ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist << PLIST
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
		<string>/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh</string>
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

echo "   ✓ LaunchAgent 已更新"

echo ""
echo "5. 重新加载服务..."
launchctl unload ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist 2>/dev/null
sleep 1
launchctl load ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist 2>/dev/null
brew services stop elasticsearch-full
sleep 2
brew services start elasticsearch-full

echo ""
echo "等待服务启动（10秒）..."
sleep 10

echo ""
echo "6. 验证服务..."
if curl -s http://localhost:9200 > /dev/null 2>&1; then
    echo "✓ Elasticsearch 启动成功！"
    echo ""
    echo "版本信息:"
    curl -s http://localhost:9200 | head -5
    echo ""
    brew services list | grep elasticsearch
else
    echo "⚠ Elasticsearch 可能还在启动中或启动失败"
    echo ""
    echo "查看日志:"
    tail -20 /opt/homebrew/var/log/elasticsearch.log
    echo ""
    echo "服务状态:"
    brew services list | grep elasticsearch
fi

echo ""
echo "=========================================="
echo "修复完成！"
echo "=========================================="
