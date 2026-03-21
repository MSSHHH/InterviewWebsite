# HotKey Dashboard 登录问题解决方案

## 问题描述

访问 HotKey Dashboard 时出现错误：
```
业务异常：未登录
com.jd.platform.hotkey.dashboard.common.ex.BizException: null
at com.jd.platform.hotkey.dashboard.interceptor.JwtInterceptor.preHandle
```

这是因为 HotKey Dashboard 需要登录认证才能访问。

---

## 解决方案

### 方案一：使用默认账号登录（推荐）

HotKey Dashboard 通常有默认的登录账号：

**默认账号信息**：
- 用户名：`admin`
- 密码：`admin`

或者：
- 用户名：`hotkey`
- 密码：`hotkey`

**登录步骤**：
1. 访问 Dashboard：http://localhost:8081
2. 在登录页面输入默认账号密码
3. 登录成功后即可访问

---

### 方案二：查看 Dashboard 启动日志

Dashboard 启动时可能会在日志中显示默认账号信息，查看启动日志：

```bash
# 如果 Dashboard 是通过 jar 包启动的
cat hotkey/hotkey-dashboard.log | grep -i "login\|password\|admin"

# 或者查看控制台输出
```

---

### 方案三：配置 Dashboard（如果支持）

如果 Dashboard 支持配置文件，可以：

1. **查找配置文件**：
   ```bash
   # 查找 Dashboard 配置文件
   find . -name "*dashboard*" -type f | grep -i "config\|application\|properties"
   ```

2. **修改配置**（如果找到）：
   ```properties
   # 禁用登录（如果支持）
   dashboard.auth.enabled=false
   
   # 或修改默认密码
   dashboard.admin.username=admin
   dashboard.admin.password=your_password
   ```

---

### 方案四：通过 API 登录（如果支持）

有些 Dashboard 支持通过 API 登录：

```bash
# 尝试登录 API
curl -X POST http://localhost:8081/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

---

## 快速测试

### 1. 尝试默认账号

在浏览器访问：http://localhost:8081

尝试以下账号组合：
- `admin` / `admin`
- `hotkey` / `hotkey`
- `root` / `root`
- `admin` / `123456`

### 2. 查看 Dashboard 文档

查看 HotKey Dashboard 的 GitHub 仓库或文档：
- https://github.com/jd-platform/hotkey
- 查看 README 或 Wiki 中的登录说明

---

## 临时解决方案（如果不需要 Dashboard）

如果只是测试 HotKey 功能，**Dashboard 不是必需的**：

1. **Worker 和 etcd 正常运行即可**
2. **应用可以正常连接 HotKey**
3. **功能测试可以通过应用日志和性能测试验证**

Dashboard 主要用于：
- 监控热 key 统计
- 查看应用连接状态
- 可视化展示

**不影响 HotKey 核心功能！**

---

## 验证 HotKey 是否正常工作（不依赖 Dashboard）

### 1. 查看应用日志

```bash
# 查看应用启动日志，应该看到 HotKey 客户端初始化成功
grep -i "hotkey\|etcd" mianshiya-next-backend/logs/*.log
```

### 2. 运行性能测试

```bash
./test_hotkey_quick.sh
```

如果响应时间明显减少，说明 HotKey 正常工作。

### 3. 检查 etcd 中的数据

```bash
# 使用 etcdctl 查看 HotKey 相关数据
cd etcd/etcd-v3.5.15-darwin-arm64
./etcdctl get --prefix /hotkey
```

---

## 常见默认账号列表

尝试以下常见默认账号：

| 用户名 | 密码 | 说明 |
|--------|------|------|
| admin | admin | 最常见 |
| admin | 123456 | 常见 |
| hotkey | hotkey | HotKey 专用 |
| root | root | 常见 |
| admin | (空) | 部分系统 |

---

## 下一步

1. **先尝试默认账号登录**
2. **如果无法登录，查看 Dashboard 启动日志**
3. **如果不需要 Dashboard，可以忽略此错误，直接测试 HotKey 功能**

**重要**：Dashboard 登录错误不影响 HotKey 的核心功能（热 key 探测和缓存）！
