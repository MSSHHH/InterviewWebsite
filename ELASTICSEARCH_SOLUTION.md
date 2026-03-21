# Elasticsearch 后台启动问题 - 完整解决方案

## 问题分析

通过 Homebrew 安装的 `elasticsearch-full` 后台启动失败，错误信息：
```
could not find java in bundled JDK at /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/jdk.app/Contents/Home/bin/java
```

**根本原因**：
- Elasticsearch 7.17.4 需要 Java 11+
- Homebrew 安装的版本缺少捆绑的 JDK
- LaunchAgent 启动时环境变量未正确传递

## 解决方案

### 方案一：使用前台启动（最简单，推荐用于开发环境）

如果只是开发测试，可以使用前台启动：

```bash
# 设置环境变量
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"

# 前台启动
/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch

# 或使用 nohup 后台运行
nohup /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch > /opt/homebrew/var/log/elasticsearch.log 2>&1 &
```

### 方案二：修改 elasticsearch-env 脚本（推荐）

直接修改 Elasticsearch 的环境脚本，强制使用系统 Java：

```bash
# 1. 备份
cp /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch-env \
   /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch-env.backup

# 2. 在脚本开头添加强制设置（在第 1 行之后）
cat >> /tmp/es_env_patch.txt << 'PATCH'
# 强制使用系统 Java 11
if [ -z "$ES_JAVA_HOME" ]; then
    export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
fi
PATCH

# 3. 在 elasticsearch-env 的第 2 行插入
sed -i '' '2 r /tmp/es_env_patch.txt' /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch-env
```

### 方案三：使用 systemd 或手动管理（macOS 推荐）

创建启动脚本：

```bash
# 创建启动脚本
cat > /opt/homebrew/opt/elasticsearch-full/bin/start-elasticsearch.sh << 'SCRIPT'
#!/bin/bash
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
export PATH="$ES_JAVA_HOME/bin:$PATH"
exec /opt/homebrew/Cellar/elasticsearch-full/7.17.4/libexec/bin/elasticsearch "$@"
SCRIPT

chmod +x /opt/homebrew/opt/elasticsearch-full/bin/start-elasticsearch.sh

# 使用 nohup 启动
nohup /opt/homebrew/opt/elasticsearch-full/bin/start-elasticsearch.sh > /opt/homebrew/var/log/elasticsearch.log 2>&1 &
```

### 方案四：重新安装带 JDK 的版本

如果以上方案都不行，可以考虑：

1. **卸载当前版本**：
   ```bash
   brew services stop elasticsearch-full
   brew uninstall elasticsearch-full
   ```

2. **安装标准版本**（包含 JDK）：
   ```bash
   brew install elasticsearch
   ```

3. **或使用 Docker**：
   ```bash
   docker run -d -p 9200:9200 -p 9300:9300 \
     -e "discovery.type=single-node" \
     docker.elastic.co/elasticsearch/elasticsearch:7.17.4
   ```

## 快速修复命令（一键执行）

```bash
# 创建修复脚本并执行
cat > /tmp/fix_es.sh << 'FIX'
#!/bin/bash
export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
brew services stop elasticsearch-full
nohup /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch > /opt/homebrew/var/log/elasticsearch.log 2>&1 &
sleep 5
curl -s http://localhost:9200 && echo "✓ Elasticsearch 启动成功" || echo "✗ 启动失败，查看日志: tail -f /opt/homebrew/var/log/elasticsearch.log"
FIX

chmod +x /tmp/fix_es.sh
/tmp/fix_es.sh
```

## 验证

启动后验证：
```bash
curl http://localhost:9200
```

应该返回 Elasticsearch 的 JSON 响应。

## 配置应用连接

确保 `application.yml` 中的配置正确：

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    username: root      # 如果启用了安全功能
    password: 123456    # 如果启用了安全功能
```

**注意**：如果 Elasticsearch 没有启用安全功能，可以移除 username 和 password 配置。

## 常见问题

### 1. 仍然找不到 Java

确保 Java 11 已安装：
```bash
brew install openjdk@11
ls -la /opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home/bin/java
```

### 2. 端口被占用

```bash
lsof -i :9200
kill -9 <PID>
```

### 3. 内存不足

编辑 `/opt/homebrew/etc/elasticsearch/jvm.options`，调整堆内存：
```
-Xms512m
-Xmx512m
```

## 推荐方案

**对于开发环境**：使用方案一（前台启动）或方案三（手动管理）

**对于生产环境**：使用 Docker 或重新安装标准版本

## 临时解决方案

如果急需使用，可以：

1. **停止 brew services**：
   ```bash
   brew services stop elasticsearch-full
   ```

2. **手动启动**：
   ```bash
   export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
   nohup /opt/homebrew/opt/elasticsearch-full/bin/elasticsearch > /opt/homebrew/var/log/elasticsearch.log 2>&1 &
   ```

3. **添加到 ~/.zshrc 或 ~/.bashrc**（可选）：
   ```bash
   export ES_JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
   ```

这样每次手动启动时都会自动使用正确的 Java。
