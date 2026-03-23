# 系统可用性保障代码总结

## 1. 这条项目描述对应了哪些代码

简历里的这句话：

> 系统可用性保障：基于 Sentinel 注解 + Dashboard 对核心接口进行限流与熔断降级，异常时通过本地缓存兜底；同时结合 Redis 访问频控、Lua 脚本原子计数、自动告警与封禁机制，提升系统可用性与安全性。

在当前仓库里，实际上对应 4 块能力：

1. Sentinel 限流、熔断与降级
2. 本地缓存兜底
3. Redis 访问频控 + Lua 原子计数
4. 自动告警、封禁与 IP 黑名单过滤

对应核心代码：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelConstant.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/MainApplication.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/manager/CounterManager.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/BlackIpFilter.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/BlackIpUtils.java`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/NacosListener.java`

需要先说明一个非常重要的点：

- 这条描述里既有“系统可用性”能力，也有“防刷 / 安全治理”能力
- 它们在代码里不是一套统一中间件，而是多层组合设计

---

## 2. Sentinel：核心接口的限流、熔断与降级

### 2.1 Sentinel 资源定义

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelConstant.java:14`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelConstant.java:19`

当前定义了两个核心资源：

```java
String listQuestionBankVOByPage = "listQuestionBankVOByPage";
String listQuestionVOByPage = "listQuestionVOByPage";
```

也就是：

1. 题库分页列表接口
2. 题目分页列表接口

---

### 2.2 Sentinel Dashboard 接入

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/MainApplication.java:27`

启动时会先执行：

```java
initSentinelSystemProperties();
```

并设置：

```java
setIfAbsent("csp.sentinel.dashboard.server", "SENTINEL_DASHBOARD", "localhost:8858");
setIfAbsent("csp.sentinel.api.port", "SENTINEL_PORT", "8719");
```

这段代码的作用是：

1. 给 Sentinel 设置 Dashboard 地址
2. 给当前应用设置命令端口
3. 保证应用实例能被 Sentinel Dashboard 感知和管理

也就是说，项目不是只在本地写了规则类，而是预留了与 Dashboard 联动的入口。

---

### 2.3 Sentinel 规则管理

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:33`

项目启动后，会初始化：

1. 限流规则
2. 熔断规则
3. 本地文件持久化监听

---

### 2.4 限流规则

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:43`

当前有两条核心限流规则：

#### 规则 1：题目列表接口按 IP 做热点参数限流

```java
ParamFlowRule questionParamRule = new ParamFlowRule(SentinelConstant.listQuestionVOByPage)
        .setParamIdx(0)
        .setCount(60)
        .setDurationInSec(60);
```

含义：

- 针对 `listQuestionVOByPage`
- 按第 0 个参数做热点参数限流
- 在当前代码里，第 0 个参数实际传的是客户端 IP
- 单 IP 每 60 秒最多 60 次

#### 规则 2：题库列表接口做普通 QPS 限流

```java
FlowRule questionBankListFlowRule = new FlowRule(SentinelConstant.listQuestionBankVOByPage)
        .setGrade(RuleConstant.FLOW_GRADE_QPS)
        .setCount(30);
```

含义：

- `listQuestionBankVOByPage`
- QPS 超过 30 时开始限流

---

### 2.5 熔断规则

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:61`

当前定义了 3 条降级规则：

#### 规则 1：题目列表慢调用比例熔断

```java
DegradeRule slowCallRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
        .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
        .setCount(0.2)
        .setTimeWindow(60)
        .setStatIntervalMs(30 * 1000)
        .setMinRequestAmount(10)
        .setSlowRatioThreshold(3);
```

含义：

- 最近 30 秒内
- 请求数至少 10 次
- 超过 20% 的请求慢于 3 秒
- 则熔断 60 秒

#### 规则 2：题目列表异常比例熔断

```java
DegradeRule errorRateRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
        .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
        .setCount(0.1)
        .setTimeWindow(60)
        .setStatIntervalMs(30 * 1000)
        .setMinRequestAmount(10);
```

含义：

- 最近 30 秒
- 请求数至少 10 次
- 错误率超过 10%
- 则熔断 60 秒

#### 规则 3：题库列表异常比例熔断

```java
DegradeRule questionBankErrorRateRule = new DegradeRule(SentinelConstant.listQuestionBankVOByPage)
        .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
        .setCount(0.1)
        .setTimeWindow(60)
        .setStatIntervalMs(30 * 1000)
        .setMinRequestAmount(10);
```

这条规则主要是为了给题库列表接口触发 fallback 留出空间。

---

### 2.6 规则持久化与 Dashboard 配合

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:93`

这里做了两件事：

1. 读取项目根目录下的 `sentinel/FlowRule.json` 和 `sentinel/DegradeRule.json`
2. 注册读写数据源，把规则和本地文件联动起来

这意味着：

- 规则不是完全写死在内存里
- 可以通过 Sentinel Dashboard 或文件更新机制动态调整

这是“Sentinel 注解 + Dashboard”这句项目描述的落点之一。

---

## 3. 异常时通过本地缓存兜底：当前仓库里是谁在做

这部分需要按真实代码来讲，不能笼统说“所有 Sentinel 接口都回本地缓存”。

### 3.1 真正做了本地缓存兜底的是题库列表接口

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:256`

接口：

```java
@PostMapping("/list/page/vo")
@SentinelResource(
    value = SentinelConstant.listQuestionBankVOByPage,
    blockHandler = "handleBlockException",
    fallback = "handleFallback"
)
```

正常路径：

1. 先生成 `listCacheKey`
2. 先查 `questionBankListLocalCache`
3. 本地缓存未命中才查数据库
4. 查完回填本地缓存

核心代码：

```java
Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
if (localCachePage != null) {
    return ResultUtils.success(localCachePage);
}
Page<QuestionBank> questionBankPage = questionBankService.page(...);
Page<QuestionBankVO> questionBankVOPage = questionBankService.getQuestionBankVOPage(...);
questionBankListLocalCache.put(listCacheKey, questionBankVOPage);
```

---

### 3.2 被限流或熔断后如何兜底

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:285`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:298`

`blockHandler`：

```java
if (ex instanceof DegradeException) {
    return handleFallback(questionBankQueryRequest, request, ex);
}
return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
```

`fallback`：

```java
Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
if (localCachePage != null) {
    return ResultUtils.success(localCachePage);
}
return ResultUtils.success(new Page<>(current, pageSize, 0));
```

这里体现的是标准的可用性优先思路：

1. 降级时先尝试返回本地缓存
2. 如果本地缓存也没有，再返回空分页
3. 避免在异常状态下继续把数据库打爆

这正是“异常时通过本地缓存兜底”的最直接代码体现。

---

### 3.3 题目列表 Sentinel 版接口也做了限流 / 熔断，但 fallback 目前没有本地缓存

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java:258`

这个接口：

```java
@PostMapping("/list/page/vo/sentinel")
public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(...)
```

在实现上使用了手动埋点：

```java
entry = SphU.entry(SentinelConstant.listQuestionVOByPage, EntryType.IN, 1, remoteAddr);
```

说明：

- 题目列表接口也有 Sentinel 保护
- 而且它是按 IP 做热点参数限流

但是它的 `handleFallback` 当前实现是：

```java
return ResultUtils.success(null);
```

也就是说：

- 这条链路有熔断降级
- 但当前仓库里没有像题库列表那样真正回本地缓存

如果面试时想讲准确，建议说：

> 当前代码里“本地缓存兜底”主要落在题库分页列表接口；题目分页列表的 Sentinel 版接口目前更偏向演示限流 / 熔断流程，fallback 返回的是空值。

---

## 4. Redis 访问频控：题目详情接口的防刷保护

### 4.1 频控入口在哪

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java:164`

题目详情接口：

```java
@GetMapping("/get/vo")
public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request)
```

在查数据库之前，会先做一层爬虫检测：

```java
User loginUser = userService.getLoginUserPermitNull(request);
if (loginUser != null) {
    crawlerDetect(loginUser.getId());
}
```

说明：

- 只有登录用户才会进入这套计数逻辑
- 未登录用户当前不会触发这个基于用户维度的频控

---

### 4.2 频控规则是什么

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java:189`

核心阈值：

```java
final int WARN_COUNT = 10;
final int BAN_COUNT = 20;
```

访问 key：

```java
String key = String.format("user:access:%s", loginUserId);
```

调用计数：

```java
long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
```

这段的含义是：

- 对用户访问行为按“1 分钟窗口”计数
- Redis key 会按时间桶自动拼接
- key 额外保留 180 秒，便于统计与排查

---

### 4.3 达到阈值后如何处理

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java:199`

#### 超过 20 次：封号

```java
if (count > BAN_COUNT) {
    StpUtil.kickout(loginUserId);
    User updateUser = new User();
    updateUser.setId(loginUserId);
    updateUser.setUserRole("ban");
    userService.updateById(updateUser);
    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，已被封号");
}
```

这里做了 3 件事：

1. 踢当前用户下线
2. 更新数据库，把用户角色改成 `ban`
3. 直接拒绝后续请求

用户角色枚举定义在：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/enums/UserRoleEnum.java:18`

```java
BAN("被封号", "ban");
```

#### 正好等于 10 次：告警

```java
if (count == WARN_COUNT) {
    throw new BusinessException(110, "警告：访问太频繁");
}
```

这里要特别说明：

- 当前“自动告警”实现是抛出一个业务异常给前端
- 注释里写了“可以改为向管理员发送邮件通知”
- 也就是说，仓库中已经有告警触发点，但还不是完整的邮件 / webhook / 短信通知系统

如果面试官追问，要如实说这是“轻量版自动告警”。

---

## 5. Lua 脚本原子计数：为什么不用普通 incr + expire

### 5.1 实现位置

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/manager/CounterManager.java:71`

核心逻辑是：

1. 先根据时间粒度计算时间桶
2. 拼接出最终 Redis key
3. 用 Lua 脚本一次性执行：
   - `exists`
   - `incr`
   - `set`
   - `expire`

---

### 5.2 时间桶设计

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/manager/CounterManager.java:76`

项目会根据时间单位计算一个 `timeFactor`：

```java
String redisKey = key + ":" + timeFactor;
```

例如原始 key 是：

```text
user:access:123
```

最终 Redis key 可能变成：

```text
user:access:123:29346211
```

这样做的意义是：

- 每个时间窗口自动形成独立统计桶
- 无需手动清空计数器
- 过期后自动淘汰

---

### 5.3 Lua 脚本内容

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/manager/CounterManager.java:95`

脚本逻辑：

```lua
if redis.call('exists', KEYS[1]) == 1 then
  return redis.call('incr', KEYS[1]);
else
  redis.call('set', KEYS[1], 1);
  redis.call('expire', KEYS[1], ARGV[1]);
  return 1;
end
```

含义：

1. 如果 key 已存在，直接自增
2. 如果 key 不存在，先初始化为 1
3. 同时设置过期时间
4. 返回最新计数值

---

### 5.4 为什么必须用 Lua

如果用普通 Java 代码写成两步：

1. `INCR`
2. `EXPIRE`

会有并发一致性风险：

- 线程 A 刚 `INCR`
- 线程 B 也在并发操作
- 中间如果服务异常、网络抖动、命令交错，可能出现 key 没设置过期时间
- 最终会产生“脏计数器”或窗口统计不准

Lua 的优势在于：

- Redis 会把脚本当作一个原子操作执行
- `set + expire + incr` 不会被其他命令插入

所以这部分非常适合面试时强调：

> 频控统计的核心不是单纯把计数放进 Redis，而是通过 Lua 保证初始化、自增和过期时间设置的原子性。

---

## 6. 自动封禁之外，还有一层 IP 黑名单防线

### 6.1 全局请求过滤器

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/BlackIpFilter.java:16`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/MainApplication.java:22`

`BlackIpFilter` 使用了：

```java
@WebFilter(urlPatterns = "/*", filterName = "blackIpFilter")
```

而主类开启了：

```java
@ServletComponentScan
```

这意味着：

- 黑名单过滤器会在全局请求入口处生效
- 比 Controller 更早拦截请求

核心逻辑：

```java
String ipAddress = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
if (BlackIpUtils.isBlackIp(ipAddress)) {
    servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}");
    return;
}
```

也就是说：

- 命中黑名单 IP 的请求不会进入业务层
- 直接在过滤器阶段被拦截

---

### 6.2 黑名单存储结构：BloomFilter

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/BlackIpUtils.java:21`

当前使用的是：

```java
private static BitMapBloomFilter bloomFilter = new BitMapBloomFilter(100);
```

`rebuildBlackIp` 里会重建新的 BloomFilter：

```java
BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
for (String blackIp : blackIpList) {
    bitMapBloomFilter.add(blackIp);
}
bloomFilter = bitMapBloomFilter;
```

这里用 BloomFilter 的原因很典型：

1. 黑名单 IP 判断只需要“是否在集合中”
2. BloomFilter 内存占用比普通 `HashSet` 更低
3. 适合高并发入口快速判定

这是“系统可用性 + 安全性”里很常见的一道防线。

---

### 6.3 IP 获取逻辑

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/utils/NetUtils.java:20`

这个工具类会优先读取：

- `x-forwarded-for`
- `Proxy-Client-IP`
- `WL-Proxy-Client-IP`
- `request.getRemoteAddr()`

说明作者考虑了反向代理场景，不是简单地直接取 `RemoteAddr`。

---

### 6.4 黑名单可以动态同步，但当前默认没启用

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/blackfilter/NacosListener.java:25`

这里有一个非常重要的现状：

```java
// todo 取消注释开启 Nacos（须先配置 Nacos）
//@Component
public class NacosListener implements InitializingBean
```

说明：

- 黑名单动态同步的代码已经写好了
- 但当前默认没有启用
- 只有手动恢复 `@Component` 并完成 Nacos 配置后，这部分才会真正工作

其逻辑是：

1. 从 Nacos 拉取黑名单配置
2. 初始化 BloomFilter
3. 后续监听配置变更
4. 一旦黑名单变化，重建本地 BloomFilter

所以更准确的说法是：

- 仓库中具备“自动同步 IP 黑名单”的设计
- 但默认运行态下，这一块属于预留能力，不是默认开启

---

## 7. 这一整套能力是如何提升系统可用性与安全性的

可以按层来理解：

### 第一层：接口级限流和熔断

- Sentinel 对题库列表、题目列表进行流量治理
- 当系统慢调用或异常率升高时，主动限流 / 熔断

### 第二层：本地缓存兜底

- 题库分页列表在异常时优先返回 Caffeine 本地缓存
- 缓存没有再返回空分页
- 避免数据库继续承压

### 第三层：用户维度防刷

- 登录用户访问题目详情时，按用户维度做分钟级访问计数
- 超过阈值先告警、再封号

### 第四层：IP 维度入口封堵

- 黑名单 IP 在过滤器阶段被直接拦截
- 业务层不再承受这些流量

这四层组合起来，形成了：

- 流量治理
- 故障降级
- 用户行为风控
- 网络入口防护

所以这条项目描述从技术上是成立的，但它其实是多套机制共同完成的。

---

## 8. 面试时怎么讲最顺

建议你按下面这个顺序讲：

1. 我对核心高频接口接入了 Sentinel，分别做了 QPS 限流、热点参数限流和异常率 / 慢调用比例熔断。
2. 对题库分页列表接口，我在 Sentinel 降级后优先返回 Caffeine 本地缓存，没有缓存再返回空分页，从而避免数据库被流量高峰继续击穿。
3. 对题目详情读取，我又额外加了一层基于 Redis 的访问频控，按用户维度统计一分钟内的访问次数。
4. 计数器不是简单的 `incr + expire`，而是通过 Lua 脚本把初始化、自增和过期时间设置做成原子操作，避免并发下计数不准或 key 不过期。
5. 当访问次数达到阈值时，系统会先告警，再通过 Sa-Token 踢下线并把用户角色改成 `ban`，形成自动封禁机制。
6. 在更靠前的网络入口层，还提供了 IP 黑名单过滤和 BloomFilter 快速判定，进一步增强系统可用性和安全性。

---

## 9. 当前代码现状里需要主动说明的几个细节

这几个点如果你主动说出来，反而更显得你对代码理解扎实：

1. “异常时通过本地缓存兜底”当前主要落在题库分页列表接口，不是所有 Sentinel 接口都这么做。
2. 题目分页列表的 Sentinel 版接口目前 fallback 返回的是 `null`，更偏演示限流 / 熔断流程。
3. “自动告警”当前实现是业务异常提示，仓库里还没有真正发邮件、短信或 webhook 的通知链路。
4. IP 黑名单动态同步能力已经有代码，但 `NacosListener` 默认未启用，属于预留扩展方案。

如果你按这个粒度来讲，既不会夸大，也不会显得只是在背项目描述。
