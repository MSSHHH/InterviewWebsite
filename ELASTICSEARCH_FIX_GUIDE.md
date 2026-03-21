# Elasticsearch 后台启动问题解决方案

## 问题描述

通过 Homebrew 安装的 `elasticsearch-full` 后台启动时出现错误：
```
could not find java in bundled JDK at /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/jdk.app/Contents/Home/bin/java
```

## 问题原因

Elasticsearch 找不到捆绑的 JDK，需要配置 `ES_JAVA_HOME` 环境变量指向系统安装的 Java 11。

## 解决方案

### 方案一：使用修复脚本（推荐）

运行修复脚本：
```bash
./fix_elasticsearch_launchagent.sh
```

### 方案二：手动修复

#### 1. 确保 Java 11 已安装

```bash
brew install openjdk@11
```

#### 2. 配置环境变量

编辑 `/opt/homebrew/etc/elasticsearch/elasticsearch-env`：
```bash
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
```

#### 3. 创建包装脚本

创建 `/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh`：
```bash
#!/bin/bash
# 加载环境配置
if [ -f /opt/homebrew/etc/elasticsearch/elasticsearch-env ]; then
    source /opt/homebrew/etc/elasticsearch/elasticsearch-env
fi
# 执行 Elasticsearch
exec "/opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch" "$@"
```

设置执行权限：
```bash
chmod +x /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh
```

#### 4. 更新 LaunchAgent

编辑 `~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist`，修改 `ProgramArguments`：
```xml
<key>ProgramArguments</key>
<array>
    <string>/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh</string>
</array>
<key>EnvironmentVariables</key>
<dict>
    <key>ES_JAVA_HOME</key>
    <string>/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home</string>
</dict>
```

#### 5. 重启服务

```bash
brew services stop elasticsearch-full
launchctl unload ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist
launchctl load ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist
brew services start elasticsearch-full
```

### 方案三：使用前台启动（临时方案）

如果后台启动仍有问题，可以临时使用前台启动：

```bash
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch
```

或者使用 nohup：
```bash
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
nohup /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch > /opt/homebrew/var/log/elasticsearch.log 2>&1 &
```

## 验证

启动后验证：
```bash
curl http://localhost:9200
```

应该返回 Elasticsearch 的版本信息。

## 常见问题

### 1. 仍然找不到 Java

检查 Java 11 路径：
```bash
ls -la /opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home/bin/java
```

如果不存在，重新安装：
```bash
brew reinstall openjdk@11
```

### 2. 权限问题

确保脚本有执行权限：
```bash
chmod +x /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch-wrapper.sh
```

### 3. LaunchAgent 未加载

手动加载：
```bash
launchctl unload ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist
launchctl load ~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist
```

## 配置文件位置

- 环境配置：`/opt/homebrew/etc/elasticsearch/elasticsearch-env`
- 主配置：`/opt/homebrew/etc/elasticsearch/elasticsearch.yml`
- 日志目录：`/opt/homebrew/var/log/elasticsearch/`
- 数据目录：`/opt/homebrew/var/lib/elasticsearch/`
- LaunchAgent：`~/Library/LaunchAgents/homebrew.mxcl.elasticsearch-full.plist`

## 相关命令

```bash
# 查看服务状态
brew services list | grep elasticsearch

# 查看日志
tail -f /opt/homebrew/var/log/elasticsearch.log
tail -f /opt/homebrew/var/log/elasticsearch/elasticsearch_mhhh.log

# 重启服务
brew services restart elasticsearch-full

# 停止服务
brew services stop elasticsearch-full

# 启动服务
brew services start elasticsearch-full
```
