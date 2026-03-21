# 🔥 HotKey 启动指南

## 📋 概述

HotKey 是京东开源的热 key 探测工具，用于自动识别和缓存热点数据。本项目使用 HotKey 来优化题库详情页的访问性能。

## 🎯 启动 HotKey 需要哪些组件？

1. **etcd** - 配置中心（项目已包含）
2. **HotKey Worker** - 热 key 探测服务端（需要下载）
3. **HotKey Dashboard** - 监控界面（可选，需要下载）

---

## 🚀 快速启动（推荐）

### 方式一：使用启动脚本

```bash
chmod +x start_hotkey.sh
./start_hotkey.sh
```

选择选项 2（启动必需服务：etcd + Worker）

### 方式二：手动启动

---

## 📦 第一步：下载 HotKey Worker

### 下载地址

访问 HotKey 官方 GitHub 仓库：
**https://github.com/jd-platform/hotkey/releases**

下载以下文件：
- `hotkey-worker.jar` - Worker 服务端（必需）
- `hotkey-dashboard.jar` - Dashboard 监控界面（可选）

### 放置位置

将下载的 jar 包放置到以下任一位置：

```
InterviewWebsite/
├── hotkey/                    # 推荐：创建此目录
│   ├── hotkey-worker.jar
│   └── hotkey-dashboard.jar
└── hotkey-worker.jar         # 或直接放在项目根目录
```

---

## 🔧 第二步：启动 etcd

etcd 是 HotKey 的配置中心，项目已包含 etcd。

### macOS (ARM64)

```bash
cd etcd/etcd-v3.5.15-darwin-arm64
./etcd
```

### Linux

```bash
# 下载 etcd（如果项目中没有）
wget https://github.com/etcd-io/etcd/releases/download/v3.5.15/etcd-v3.5.15-linux-amd64.tar.gz
tar -xzf etcd-v3.5.15-linux-amd64.tar.gz
cd etcd-v3.5.15-linux-amd64
./etcd
```

### 后台运行

```bash
cd etcd/etcd-v3.5.15-darwin-arm64
nohup ./etcd > etcd.log 2>&1 &
```

### 验证 etcd

```bash
curl http://localhost:2379/health
# 应该返回: {"health":"true"}
```

---

## 🔧 第三步：启动 HotKey Worker

### 启动命令

```bash
# 如果 jar 包在 hotkey/ 目录
java -jar hotkey/hotkey-worker.jar

# 如果 jar 包在项目根目录
java -jar hotkey-worker.jar
```

### 后台运行

```bash
nohup java -jar hotkey/hotkey-worker.jar > hotkey-worker.log 2>&1 &
```

### 验证 Worker

```bash
curl http://localhost:9527
# 应该返回 Worker 的状态信息
```

---

## 🔧 第四步：启动 HotKey Dashboard（可选）

Dashboard 用于监控热 key 的探测情况，不是必需的。

### 启动命令

```bash
java -jar hotkey/hotkey-dashboard.jar
```

### 后台运行

```bash
nohup java -jar hotkey/hotkey-dashboard.jar > hotkey-dashboard.log 2>&1 &
```

### 访问 Dashboard

浏览器打开：**http://localhost:8081**

---

## ✅ 验证 HotKey 配置

### 1. 检查服务状态

运行环境检查脚本：

```bash
./check_env.sh
```

应该看到：
- ✅ etcd 运行正常（端口 2379）
- ✅ HotKey Worker 运行正常（端口 9527）
- ✅ HotKey Dashboard 运行正常（端口 8081，如果已启动）

### 2. 检查应用配置

确认 `application.yml` 中的配置：

```yaml
hotkey:
  app-name: mianshiya
  caffeine-size: 10000
  push-period: 1000
  etcd-server: http://localhost:2379
```

### 3. 检查 jar 包

确认 HotKey Client jar 包存在：

```bash
ls mianshiya-next-backend/lib/hotkey-client-0.0.4-SNAPSHOT.jar
```

---

## 🧪 测试 HotKey 功能

### 1. 启动 Spring Boot 应用

```bash
cd mianshiya-next-backend
mvn spring-boot:run
```

### 2. 查看启动日志

应用启动时，应该看到 HotKey 客户端初始化的日志：

```
[HotKey] Client started successfully
```

### 3. 高并发访问测试

使用 curl 或 Postman 高并发访问题库接口：

```bash
# 高并发访问同一个题库
for i in {1..100}; do
  curl "http://localhost:8101/api/questionBank/get/vo?id=1" &
done
wait
```

### 4. 观察效果

- **前几次请求**：从数据库查询（较慢）
- **后续请求**：从本地缓存返回（很快）
- **Dashboard**：可以看到该 key 被标记为热 key

---

## 📊 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| etcd | 2379 | 配置中心 |
| HotKey Worker | 9527 | 热 key 探测服务端 |
| HotKey Dashboard | 8081 | 监控界面 |

---

## 🛠️ 常见问题

### 1. etcd 启动失败

**问题**：端口 2379 被占用

**解决**：
```bash
# 查看占用端口的进程
lsof -i :2379

# 杀死进程
kill -9 <PID>

# 或使用其他端口（需要修改配置）
./etcd --listen-client-urls http://localhost:2378
```

### 2. HotKey Worker 启动失败

**问题**：找不到 jar 包

**解决**：
- 确认 jar 包路径正确
- 检查文件权限：`chmod +x hotkey-worker.jar`

**问题**：端口 9527 被占用

**解决**：
```bash
lsof -i :9527
kill -9 <PID>
```

### 3. 应用无法连接 HotKey

**问题**：应用启动时 HotKey 客户端初始化失败

**检查清单**：
- [ ] etcd 是否运行
- [ ] HotKey Worker 是否运行
- [ ] `etcd-server` 配置是否正确
- [ ] 网络连接是否正常

**查看日志**：
```bash
# 查看应用日志
tail -f mianshiya-next-backend/logs/application.log

# 查看 HotKey Worker 日志
tail -f hotkey/hotkey-worker.log
```

### 4. 热 key 检测不生效

**可能原因**：
1. 访问量不够大（未达到阈值）
2. HotKey Worker 未正常运行
3. 应用配置错误

**排查步骤**：
1. 检查 Dashboard 是否有数据
2. 增加并发访问量
3. 查看应用和 Worker 的日志

---

## 📝 启动顺序

正确的启动顺序：

```
1. 启动 etcd
   ↓
2. 启动 HotKey Worker
   ↓
3. 启动 HotKey Dashboard（可选）
   ↓
4. 启动 Spring Boot 应用
```

---

## 🎯 快速命令汇总

```bash
# 1. 启动 etcd（后台）
cd etcd/etcd-v3.5.15-darwin-arm64
nohup ./etcd > etcd.log 2>&1 &

# 2. 启动 HotKey Worker（后台）
nohup java -jar hotkey/hotkey-worker.jar > hotkey-worker.log 2>&1 &

# 3. 启动 HotKey Dashboard（后台，可选）
nohup java -jar hotkey/hotkey-dashboard.jar > hotkey-dashboard.log 2>&1 &

# 4. 验证服务
./check_env.sh

# 5. 启动应用
cd mianshiya-next-backend
mvn spring-boot:run
```

---

## 🔗 相关链接

- [HotKey GitHub](https://github.com/jd-platform/hotkey)
- [HotKey 文档](https://github.com/jd-platform/hotkey/wiki)
- [etcd 官方文档](https://etcd.io/docs/)

---

## 💡 提示

- HotKey Worker 和 Dashboard 可以放在同一台机器或不同机器
- 生产环境建议使用 systemd 或 supervisor 管理服务
- 监控 Dashboard 可以帮助了解热 key 的分布情况

配置完成后，运行 `./check_env.sh` 验证所有服务！
