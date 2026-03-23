# 高并发性能优化代码总结

## 1. 这条项目描述对应了哪几部分代码

简历里的这句话：

> 面向高并发场景，基于 Redis BitMap + Redisson 实现用户年度刷题记录统计，结合 Caffeine 本地缓存与 Hotkey 热点探测机制，提升题库查询性能并防止数据库被瞬时流量击穿。

在当前仓库里，实际上对应两条不同的优化链路：

1. 用户年度刷题记录统计
   - 关键词：`Redis BitMap + Redisson + Caffeine`
   - 核心代码：
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java`
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/constant/RedisConstant.java`
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/UserController.java`

2. 题库查询性能优化与防击穿
   - 关键词：`Caffeine + HotKey + Sentinel`
   - 核心代码：
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java`
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/config/HotKeyConfig.java`
     - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java`

这条描述不是“一套技术同时干一件事”，而是“两个高并发优化点被合并成一条项目亮点”。

---

## 2. 用户年度刷题记录统计：Redis BitMap + Redisson + Caffeine

### 2.1 业务目标

要支持“统计某个用户某一年哪些天刷过题”，并且适合前端直接渲染成年历热力图。

如果用传统关系型表逐条存记录，也能实现，但在高频读场景下会有几个问题：

- 一年数据天然适合按位存储，逐条记录空间利用率不高
- 查询全年记录时需要做多条记录聚合
- 用户中心频繁刷新时，反复查库或查 Redis 成本会升高

所以这里采用了：

- Redis BitMap 作为真实签到数据源
- Redisson 作为 Java 侧的 BitMap 操作封装
- Caffeine 作为 JVM 本地短时缓存

---

### 2.2 Redis Key 设计

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/constant/RedisConstant.java:11`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/constant/RedisConstant.java:19`

Key 前缀定义为：

```java
String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";
```

实际 key 生成规则：

```java
String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId)
```

例如：

```text
user:signins:2026:123
```

含义是：

- 用户 `123`
- 在 `2026` 年的全年刷题记录

这里的建模维度很关键：

- 按“用户 + 年份”聚合
- 不按“用户 + 日期”拆成很多 key

这样做的好处是，一整年的记录都能落在一个 BitMap 里。

---

### 2.3 写入流程：把今天这一天置为 1

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:312`

核心逻辑：

```java
LocalDate date = LocalDate.now();
int year = date.getYear();
String key = RedisConstant.getUserSignInRedisKey(year, userId);
RBitSet signInBitSet = redissonClient.getBitSet(key);
int offset = date.getDayOfYear();
if (!signInBitSet.get(offset)) {
    signInBitSet.set(offset, true);
}
signInRecordLocalCache.invalidate(getUserSignInRecordCacheKey(userId, year));
```

这段逻辑可以理解成：

1. 先确定今天是当年的第几天
2. 找到这个用户这一年的 BitMap
3. 把对应天数的位置设为 `1`
4. 然后清理本地缓存，保证下次读到最新结果

对应的底层 Redis 思想其实就是：

- `GETBIT key offset`
- `SETBIT key offset 1`

这里只是通过 Redisson 的 `RBitSet` 进行了 Java 封装，没有直接手写 Redis 指令。

---

### 2.4 为什么用 BitMap

假设一年 365 天：

- 刷过题：该天 bit = `1`
- 没刷题：该天 bit = `0`

例如：

```text
第 1 天  第 2 天  第 3 天  第 4 天
   1        0        1        0
```

BitMap 适合这个场景的原因：

1. 状态天然只有两种：刷过 / 没刷过
2. 全年长度固定，最多 365 或 366 位
3. 空间开销远小于用列表或明细表存每天一条
4. 判断某一天是否刷题非常快
5. 适合做全年分布统计

---

### 2.5 读取流程：把全年 BitMap 转成前端可用的日期列表

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:339`

核心逻辑：

```java
String localCacheKey = getUserSignInRecordCacheKey(userId, year);
List<Integer> localCachedDayList = signInRecordLocalCache.getIfPresent(localCacheKey);
if (localCachedDayList != null) {
    return new ArrayList<>(localCachedDayList);
}

String key = RedisConstant.getUserSignInRedisKey(year, userId);
RBitSet signInBitSet = redissonClient.getBitSet(key);
BitSet bitSet = signInBitSet.asBitSet();

List<Integer> dayList = new ArrayList<>();
int index = bitSet.nextSetBit(0);
while (index >= 0) {
    dayList.add(index);
    index = bitSet.nextSetBit(index + 1);
}

signInRecordLocalCache.put(localCacheKey, new ArrayList<>(dayList));
return dayList;
```

执行流程如下：

1. 先查 Caffeine 本地缓存
2. 如果没命中，再通过 Redisson 获取 Redis BitMap
3. 用 `asBitSet()` 一次性把位图加载到 JVM 内存中
4. 用 `nextSetBit()` 只遍历值为 `1` 的位置
5. 返回一个 `List<Integer>` 给前端

例如返回：

```java
[1, 15, 88, 201]
```

表示：

- 这一年的第 1 天
- 第 15 天
- 第 88 天
- 第 201 天

都有刷题记录。

---

### 2.6 为什么这里还加了一层 Caffeine 本地缓存

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:56`

本地缓存配置：

```java
private final Cache<String, List<Integer>> signInRecordLocalCache = Caffeine.newBuilder()
        .initialCapacity(256)
        .maximumSize(10_000)
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .build();
```

它的作用不是替代 Redis，而是减少重复访问 Redis BitMap 的成本。

适合的场景包括：

- 用户中心页面频繁刷新
- 同一用户短时间内多次查看年度刷题记录
- 年度热力图组件反复渲染

所以这里的职责划分是：

- Redis BitMap：真实数据源
- Redisson：位图读写封装
- Caffeine：本机短时副本，加速重复读取

---

### 2.7 写后为什么要删本地缓存

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:327`

这一句非常关键：

```java
signInRecordLocalCache.invalidate(getUserSignInRecordCacheKey(userId, year));
```

如果不删缓存，会出现：

1. 用户先查了一次全年记录，本地缓存里存的是旧数据
2. 用户今天又刷了一道题，Redis BitMap 已经更新
3. 下次页面再查时，如果还命中本地缓存，就会拿到旧列表
4. 前端热力图看起来像“今天没有记录”

所以这里采用的是典型的：

- 写 Redis
- 删本地缓存
- 下次读取再重新加载

这是一种标准的“写后删缓存”策略。

---

### 2.8 对外接口

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/UserController.java:352`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/UserController.java:367`

接口分别是：

1. `POST /user/add/sign_in`
   - 写入当天刷题记录

2. `GET /user/get/sign_in?year=2026`
   - 读取某一年的刷题记录

---

### 2.9 这一部分面试时可以怎么讲

可以这样讲：

> 用户年度刷题记录我没有按天存明细，而是按“用户 + 年份”建 Redis BitMap，用 Redisson 的 `RBitSet` 读写当天是否刷题。查询时一次性把全年位图拉成 Java `BitSet`，遍历所有置位日期返回给前端日历组件。为了减少重复访问 Redis 的开销，我又叠加了一层 Caffeine 本地缓存，并且在写入后主动失效缓存，保证前端看到的是最新数据。

---

## 3. 题库查询性能优化：Caffeine + HotKey + Sentinel

### 3.1 先说结论

这一块不是单一缓存方案，而是三层组合：

1. HotKey
   - 用来识别和承接热点题库详情请求

2. Caffeine
   - 用来承接题库列表的本地缓存和降级兜底

3. Sentinel
   - 用来限流、熔断和触发 fallback

目标是：

- 常规请求优先走本地缓存
- 热点详情请求优先走 HotKey 本地热点缓存
- 异常流量下触发 Sentinel 限流 / 熔断
- 熔断后优先返回本地缓存，避免数据库被继续打爆

---

### 3.2 Caffeine 本地缓存定义

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:67`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:76`

当前类里声明了两个 Caffeine 缓存：

```java
private final Cache<String, QuestionBankVO> questionBankDetailLocalCache
private final Cache<String, Page<QuestionBankVO>> questionBankListLocalCache
```

其中：

1. `questionBankDetailLocalCache`
   - 语义上是题库详情本地缓存
   - 但在当前版本代码里，详情主读取链路已经直接改成了 HotKey
   - 也就是说，这个字段目前更多是历史设计残留 / 预留兜底，不是详情主链路的核心读取来源

2. `questionBankListLocalCache`
   - 当前仍在真实使用
   - 主要用于题库分页列表查询
   - 同时承担 Sentinel fallback 的本地兜底

这点在讲解时一定要按“当前代码真实状态”来讲，不要把旧版逻辑和现状混在一起。

---

### 3.3 HotKey 配置如何接入

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/config/HotKeyConfig.java:48`

初始化逻辑：

```java
ClientStarter starter = builder.setAppName(appName)
        .setCaffeineSize(caffeineSize)
        .setPushPeriod(pushPeriod)
        .setEtcdServer(etcdServer)
        .build();
starter.startPipeline();
```

这里配置了：

- `appName`
- `etcdServer`
- `caffeineSize`
- `pushPeriod`

并且有一个很重要的工程化细节：

```java
catch (Exception e) {
    started = false;
    log.error("hotkey client start failed, fallback to local cache only", e);
}
```

说明 HotKey 启动失败不会把主业务链路拖死，而是降级为“只走本地缓存 / 正常数据库逻辑”。

这点非常适合面试时强调：

- HotKey 是增强能力，不是单点依赖
- 失败后系统仍可用，只是热点加速能力下降

---

### 3.4 题库详情接口：当前实现如何走 HotKey

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:187`

当前实现的主要逻辑：

```java
String key = "bank_detail_" + id;
if (JdHotKeyStore.isHotKey(key)) {
    Object cachedQuestionBankVO = JdHotKeyStore.get(key);
    if (cachedQuestionBankVO != null) {
        return ResultUtils.success((QuestionBankVO) cachedQuestionBankVO);
    }
}

QuestionBank questionBank = questionBankService.getById(id);
QuestionBankVO questionBankVO = questionBankService.getQuestionBankVO(questionBank, request);

if (needQueryQuestionList) {
    // 继续查题目分页数据
}

JdHotKeyStore.smartSet(key, questionBankVO);
return ResultUtils.success(questionBankVO);
```

它可以拆成 4 步：

1. 生成热点 key
   - `bank_detail_{id}`

2. 判断当前 key 是否已经被 HotKey 识别为热点
   - `JdHotKeyStore.isHotKey(key)`

3. 如果本地热点缓存里有值，直接返回
   - 避免继续查数据库

4. 如果没有命中，就查库并组装 `QuestionBankVO`
   - 然后调用 `smartSet` 回填热点缓存

---

### 3.5 HotKey 到底缓存了什么

当前这版代码里，HotKey 缓存的是：

- `QuestionBankVO`

也就是题库详情接口最终返回给前端的封装对象。

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:226`

```java
JdHotKeyStore.smartSet(key, questionBankVO);
```

所以不是只缓存“这个 key 很热”的标记，而是把真正的数据对象存进了 HotKey 客户端的本地缓存。

---

### 3.6 HotKey 如何决定“谁该被缓存”

不是 `smartSet` 决定热点。

真正的流程是：

1. 业务请求先调用 `isHotKey(key)`
2. HotKey SDK 上报 key 访问热度
3. Worker 根据一段时间内的访问量判断某个 key 是否足够热
4. 客户端收到热 key 推送后，在本地将它标记为热点
5. 之后业务调用 `smartSet(key, value)` 时，才会真正把值写入热点缓存

所以更准确的说法是：

- `isHotKey` 负责参与热度判断
- `smartSet` 负责在“已是热点”的前提下回填数据值

它是“先判热，再缓存”，不是“调用 `smartSet` 就一定缓存”。

---

### 3.7 当前代码里 HotKey 实现的一个注意点

当前详情 key 的设计是：

```java
String key = "bank_detail_" + id;
```

也就是说只使用了题库 `id`，没有把 `needQueryQuestionList`、`current`、`pageSize` 等参数带进去。

这会带来一个语义风险：

- 不带题目列表的轻量详情
- 带题目分页数据的重详情

当前会共用同一个热点 key。

这意味着缓存里的 `QuestionBankVO` 可能在不同请求语义之间混用：

- 有时带 `questionPage`
- 有时不带 `questionPage`

因此如果要把文档讲到完全准确，应该明确说明：

- 当前 HotKey 已接入并在详情接口生效
- 但 key 粒度仍有进一步优化空间

---

### 3.8 题库列表接口：Caffeine 本地缓存 + Sentinel 保护

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:256`

当前列表接口使用了：

```java
@SentinelResource(
    value = SentinelConstant.listQuestionBankVOByPage,
    blockHandler = "handleBlockException",
    fallback = "handleFallback"
)
```

正常读取流程：

```java
String listCacheKey = buildQuestionBankListCacheKey(questionBankQueryRequest);
Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
if (localCachePage != null) {
    return ResultUtils.success(localCachePage);
}

Page<QuestionBank> questionBankPage = questionBankService.page(...);
Page<QuestionBankVO> questionBankVOPage = questionBankService.getQuestionBankVOPage(...);
questionBankListLocalCache.put(listCacheKey, questionBankVOPage);
return ResultUtils.success(questionBankVOPage);
```

也就是：

1. 先按查询条件生成列表缓存 key
2. 先查 Caffeine 本地缓存
3. 未命中再查数据库
4. 查完后写回本地缓存

这个设计适合：

- 首页题库推荐
- 题库大全页
- 常见分页浏览

---

### 3.9 Sentinel 如何防止数据库被瞬时流量击穿

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:43`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:61`

当前和题库列表直接相关的规则有：

1. 题库列表限流规则

```java
FlowRule questionBankListFlowRule = new FlowRule(SentinelConstant.listQuestionBankVOByPage)
        .setGrade(RuleConstant.FLOW_GRADE_QPS)
        .setCount(30);
```

含义是：

- `listQuestionBankVOByPage` 这条资源 QPS 超过 30 时开始限流

2. 题库列表降级规则

```java
DegradeRule questionBankErrorRateRule = new DegradeRule(SentinelConstant.listQuestionBankVOByPage)
        .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
        .setCount(0.1)
        .setTimeWindow(60)
        .setStatIntervalMs(30 * 1000)
        .setMinRequestAmount(10);
```

含义是：

- 最近 30 秒内请求数达到阈值后
- 如果异常率超过 10%
- 则触发 60 秒熔断

---

### 3.10 被限流或熔断后怎么处理

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:285`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:298`

限流处理：

```java
return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
```

熔断降级处理：

```java
String listCacheKey = buildQuestionBankListCacheKey(questionBankQueryRequest);
Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
if (localCachePage != null) {
    return ResultUtils.success(localCachePage);
}
return ResultUtils.success(new Page<>(current, pageSize, 0));
```

这个设计非常符合“防击穿”的目标：

1. 优先返回已有本地缓存数据
2. 如果连缓存也没有，再返回空分页而不是继续压数据库

也就是说，在异常流量高压下：

- 系统优先保可用
- 不强求强一致
- 通过缓存兜底和空结果降级，避免数据库被反复击穿

---

### 3.11 缓存一致性是怎么处理的

题库发生写操作时，会主动清理缓存。

代码位置：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:115`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:147`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:176`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:366`

当前策略：

1. 新增题库
   - 清理列表缓存

2. 删除 / 更新 / 编辑题库
   - 清理详情缓存
   - 清理列表缓存

其中详情缓存失效方法：

```java
questionBankDetailLocalCache.invalidateAll();
```

这说明当前作者采取的是更保守的策略：

- 不做精细单 key 失效
- 直接整块清掉，保证安全

虽然缓存命中率未必最优，但一致性风险更低。

---

### 3.12 这一部分到底是怎么“防止数据库被瞬时流量击穿”的

从系统视角来看，这里的保护是分层完成的：

1. 第一层：本地缓存直接挡住重复读
   - 题库列表优先走 `questionBankListLocalCache`

2. 第二层：热点详情优先走 HotKey
   - 热门题库详情不再反复打数据库

3. 第三层：Sentinel 限流和熔断
   - 高峰期超阈值时直接拦截或降级

4. 第四层：fallback 返回缓存或空结果
   - 避免数据库在异常流量下继续承压

所以这里的“防击穿”不是只靠一个缓存，而是：

- 热点前移
- 本地缓存挡读
- 流量治理限压
- 异常时主动降级

---

## 4. 这条简历描述如何更准确地对应当前代码

如果严格按照当前仓库现状，可以把它理解成：

### 4.1 用户年度刷题记录统计

对应代码：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:312`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:339`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/constant/RedisConstant.java:11`

职责拆分：

- Redis BitMap：按用户和年份存全年刷题记录
- Redisson：通过 `RBitSet` 操作 Redis BitMap
- Caffeine：缓存年度刷题列表，减少重复查 Redis

### 4.2 题库查询性能优化

对应代码：

- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:187`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:256`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/config/HotKeyConfig.java:48`
- `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:43`

职责拆分：

- HotKey：承接热点题库详情
- Caffeine：承接题库列表本地缓存和 fallback 兜底
- Sentinel：限流、熔断、降级

---

## 5. 面试时建议怎么讲

建议按下面顺序讲，最顺：

1. 用户刷题记录按“用户 + 年份”存进 Redis BitMap，一天对应一位，空间成本很低。
2. 写入时通过 Redisson 的 `RBitSet` 按 `dayOfYear` 置位，查询时一次性把全年位图转成 `BitSet`，遍历出所有刷题日。
3. 为了降低重复访问 Redis 的成本，又在服务层加了 Caffeine 本地缓存，并在写入后主动失效缓存，保证数据新鲜度。
4. 题库查询这块采用了“HotKey 热点探测 + Caffeine 本地缓存 + Sentinel 限流熔断”组合方案。
5. 热门题库详情优先走 HotKey 本地热点缓存，题库分页列表优先走 Caffeine 本地缓存。
6. 当流量异常升高或错误率过高时，Sentinel 会限流或熔断，系统优先返回本地缓存或空分页，避免数据库被瞬时流量击穿。

---

## 6. 当前实现里值得主动说明的细节

如果面试官追问，你可以主动补充两个“真实实现细节”：

1. 当前代码里的 `questionBankDetailLocalCache` 字段还在，但题库详情主读取链路已经改成了 HotKey 直读，所以详情缓存的核心路径现在是 HotKey，不是这个 Caffeine 字段本身。
2. 当前 HotKey 的 key 只按题库 `id` 生成，还没有区分 `needQueryQuestionList` 等参数，这意味着热点 key 粒度还有进一步优化空间。

主动把这些说出来，反而会显得你不是只背项目话术，而是真正理解了代码现状和优化边界。
