# 数据同步设计代码分析

## 对应的项目表述

> 基于 Spring Scheduler 定时同步 MySQL 变更数据到 ES，利用唯一 ID 保证索引数据更新准确性，兼顾检索效率与数据一致性。

这句描述在当前项目中是有完整代码对应的，核心不是 binlog CDC，也不是消息队列异步双写，而是：

1. 用 Spring `@Scheduled` 做增量定时同步
2. 用一次性全量同步任务做初始化 / 重建索引
3. 用数据库主键 `id` 作为 ES 文档主键，保证覆盖更新而不是重复插入
4. 查询时让 ES 负责全文检索，让 MySQL 继续作为最新数据来源，形成“检索效率 + 数据一致性”的平衡

---

## 一、核心代码位置

### 1. 增量定时同步任务

- [`IncSyncQuestionToEs.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java)

关键点：

- [`IncSyncQuestionToEs.java#L23`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L23 )：`@Component`
- [`IncSyncQuestionToEs.java#L41`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L41 )：`@Scheduled(fixedRate = 60 * 1000)`
- [`IncSyncQuestionToEs.java#L48`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L48 )：按 `updateTime` 拉最近变更数据
- [`IncSyncQuestionToEs.java#L64`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L64 )：`saveAll` 覆盖写入 ES

### 2. 全量同步任务

- [`FullSyncQuestionToEs.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java)

关键点：

- [`FullSyncQuestionToEs.java#L23`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java#L23 )：默认注释掉 `@Component`
- [`FullSyncQuestionToEs.java#L25`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java#L25 )：实现 `CommandLineRunner`
- [`FullSyncQuestionToEs.java#L35`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java#L35 )：全量读取 MySQL 题目
- [`FullSyncQuestionToEs.java#L52`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java#L52 )：批量写入 ES

### 3. ES 文档模型与唯一 ID

- [`QuestionEsDTO.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java)

关键点：

- [`QuestionEsDTO.java#L23`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java#L23 )：`@Document(indexName = "question")`
- [`QuestionEsDTO.java#L33`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java#L33 )：`@Id private Long id;`
- [`QuestionEsDTO.java#L96`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java#L96 )：DB 实体转 ES 文档

### 4. 增量同步拉取 SQL

- [`QuestionMapper.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/mapper/QuestionMapper.java)

关键点：

- [`QuestionMapper.java#L25`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/mapper/QuestionMapper.java#L25 )

### 5. ES Repository

- [`QuestionEsDao.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/esdao/QuestionEsDao.java)

关键点：

- [`QuestionEsDao.java#L14`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/esdao/QuestionEsDao.java#L14 )：`ElasticsearchRepository<QuestionEsDTO, Long>`

### 6. 查询侧的“效率与一致性平衡”

- [`QuestionServiceImpl.java`](/Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java)

关键点：

- [`QuestionServiceImpl.java#L258`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L258 )：ES 搜索主逻辑
- [`QuestionServiceImpl.java#L285`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L285 )：动静分离
- [`QuestionServiceImpl.java#L332`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L332 )：ES 召回后回库补全最新数据
- [`QuestionServiceImpl.java#L350`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L350 )：清理 ES 脏数据

---

## 二、整体设计是怎么工作的

这个项目的题目搜索数据流可以概括成：

```text
MySQL（真实数据源）
   -> 定时任务按 updateTime 拉变更
   -> 转成 QuestionEsDTO
   -> 用数据库 id 作为 ES 文档 id 写入 Elasticsearch
   -> 搜索时先查 ES
   -> 命中题目 id 后再回 MySQL 取最新数据返回
```

这套设计的关键思想是：

- MySQL 仍然是最终权威数据源
- ES 负责全文检索和高效召回
- 定时任务负责把 MySQL 变化同步到 ES
- 查询返回前再回库补一次，减少 ES 延迟同步带来的脏读风险

所以它不是“ES 完全替代 MySQL”，而是“ES 做搜索加速，MySQL 做真实数据兜底”。

---

## 三、增量同步任务是怎么做的

### 1. 用 Spring Scheduler 周期执行

增量同步任务在 [`IncSyncQuestionToEs.java#L41`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L41 )：

```java
@Scheduled(fixedRate = 60 * 1000)
public void run() {
```

意思是：

- 每隔 1 分钟执行一次
- 不需要人工触发
- 由 Spring Scheduler 自动调度

这就是你简历里“基于 Spring Scheduler 定时同步”的直接代码依据。

### 2. 为什么不是只回看 1 分钟，而是回看 5 分钟

在 [`IncSyncQuestionToEs.java#L45`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L45 )：

```java
long FIVE_MINUTES = 5 * 60 * 1000L;
Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
```

虽然任务每 1 分钟执行一次，但查询窗口是最近 5 分钟。

这样设计是为了容错：

- 如果某一次调度延迟了
- 或者同步任务短暂失败
- 后续几轮还能把遗漏的数据补回来

这是典型的“重叠窗口”思路，用一点重复扫描换更稳的增量同步。

### 3. 拉哪些数据

在 [`QuestionMapper.java#L25`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/mapper/QuestionMapper.java#L25 )：

```java
@Select("select * from question where updateTime >= #{minUpdateTime}")
List<Question> listQuestionWithDelete(Date minUpdateTime);
```

注意这里有一个很重要的点：

- 它是按 `updateTime` 拉取最近改过的数据
- 而且**故意不过滤 `isDelete = 0`**

也就是说，这里同步的不只是：

- 新增题目
- 编辑题目

还包括：

- 逻辑删除题目

这样 ES 里的 `isDelete` 状态才能和 MySQL 对齐。

### 4. 怎么写入 ES

在 [`IncSyncQuestionToEs.java#L54`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L54 ) 开始：

```java
List<QuestionEsDTO> questionEsDTOList = questionList.stream()
        .map(QuestionEsDTO::objToDto)
        .collect(Collectors.toList());
```

先把数据库实体转成 ES 文档，再分批写入：

```java
questionEsDao.saveAll(questionEsDTOList.subList(i, end));
```

批量大小在 [`IncSyncQuestionToEs.java#L58`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java#L58 ) 定成了 `500`，这是为了避免：

- 一次提交数据太大
- 占用过多内存
- 或者单次 ES 请求过重

---

## 四、唯一 ID 为什么能保证更新准确性

这部分是这套设计里最关键的点之一。

### 1. ES 文档主键直接复用数据库主键

在 [`QuestionEsDTO.java#L30`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java#L30 ) 到 [`QuestionEsDTO.java#L33`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java#L33 )：

```java
/**
 * ES 文档主键，直接复用数据库 question.id。
 * 这样 saveAll 时天然具备幂等覆盖能力（同 id 会更新而不是新增）。
 */
@Id
private Long id;
```

也就是说：

- MySQL 里题目 `id = 1001`
- ES 里对应文档 `id` 也就是 `1001`

这是一一对应关系。

### 2. 覆盖更新而不是插入重复数据

Repository 定义在 [`QuestionEsDao.java#L14`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/esdao/QuestionEsDao.java#L14 )：

```java
public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO, Long>
```

所以：

- `saveAll(...)` 用的是 `Long` 类型主键
- 同一个 `id` 再次写入时，不会生成一条新文档
- 而是覆盖原有文档

这就是“利用唯一 ID 保证索引数据更新准确性”的真正含义。

如果没有这层设计，可能会出现：

- 一道题编辑了多次
- ES 里出现多条语义上相同、但内容不同的重复文档

而现在不会，因为它们会被同一个文档 ID 覆盖。

### 3. 这本质上是一种幂等同步

由于使用同一个主键覆盖写入，所以哪怕重叠窗口会把同一条数据同步多次：

- 不会越同步越多
- 不会产生重复索引
- 最终只会保留最新版本

这也是为什么这个项目可以放心采用“每分钟执行一次 + 回看 5 分钟”的策略。

---

## 五、全量同步任务是干什么的

全量同步任务在 [`FullSyncQuestionToEs.java`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java )。

它和增量同步的区别是：

- 增量同步：只处理最近变更
- 全量同步：把 MySQL 里的全部题目重新灌进 ES

### 适合什么场景

这个任务通常用于：

- ES 索引第一次建立
- 索引被误删后重建
- mapping 调整后重建索引
- 需要全量校正数据时

### 为什么默认没开

在 [`FullSyncQuestionToEs.java#L22`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java#L22 )：

```java
// todo 取消注释开启任务
//@Component
```

说明它默认不会随应用启动自动执行。

原因也很合理：

- 全量同步成本高
- 每次启动都全量灌 ES 不划算
- 正常运行时只需要增量同步即可

所以它被设计成“按需手动启用的一次性任务”。

---

## 六、为什么说这套设计兼顾了检索效率与数据一致性

这部分要结合查询逻辑一起看。

### 1. ES 负责高效检索

在 [`QuestionServiceImpl.java#L305`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L305 )：

```java
boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
```

也就是说：

- 标题、内容、答案都可以走 ES 全文检索
- 这是 ES 相比 MySQL `like` 的性能和能力优势所在

所以检索效率主要来自：

- 倒排索引
- 中文分词
- 多字段全文匹配

### 2. 但最终返回前，再回 MySQL 取最新数据

最关键的是 [`QuestionServiceImpl.java#L332`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L332 ) 这一段：

```java
List<Long> questionIdList = searchHits.getSearchHits().stream()
        .map(searchHit -> searchHit.getContent().getId())
        .collect(Collectors.toList());
List<Question> questionList = this.listByIds(questionIdList);
```

意思是：

- 先让 ES 找到匹配的题目 id
- 再去 MySQL 批量查这些题目的最新记录

这一步非常重要，因为它意味着：

- ES 不是最终返回结果的唯一依据
- MySQL 仍然是最新数据的准绳

这样即使 ES 同步有一点点延迟，最后返回给前端的数据仍然尽量以数据库最新状态为准。

### 3. 保持 ES 命中顺序

在 [`QuestionServiceImpl.java#L341`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L341 )：

```java
// 这里按 ES 命中顺序组装最终结果，避免 listByIds 打乱排序
```

这一步是在平衡两个目标：

- 相关性排序靠 ES
- 结果实体内容靠 MySQL

如果只是简单 `listByIds` 返回，顺序可能乱掉，搜索排序就失真了。  
所以这里又按 ES 的命中顺序重新组装了一遍。

### 4. 顺手清理 ES 脏数据

在 [`QuestionServiceImpl.java#L349`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L349 )：

```java
String deleteResult = elasticsearchRestTemplate.delete(String.valueOf(questionId), QuestionEsDTO.class);
```

如果出现：

- ES 里还有某条题目
- 但 MySQL 里已经查不到了

说明 ES 里存在脏数据。  
这时服务会顺手把它删掉。

这一步进一步加强了最终一致性。

---

## 七、动静分离是怎么和同步设计配合的

这个项目还有一个很值得讲的点，就是“动静分离”。

在 [`QuestionServiceImpl.java#L285`]( /Users/mhhh/java_learn/InterviewWebsite/mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java#L285 )：

```java
// 题库与题目的关系表是高频变更关系，不直接塞进 ES 文档，
// 检索时先查 DB 关系表拿到题目 id，再作为 terms 过滤条件约束 ES。
```

也就是说：

- 像 `questionBankId -> questionId` 这种高频变更关系
- 没有直接冗余进 ES 文档
- 而是查询时先查关系表，再把题目 id 作为 `termsQuery("id", ...)` 条件打给 ES

这样做的好处是：

- ES 文档更轻
- 关系变化不需要频繁重建 ES 文档
- 同步成本更低

这和“定时同步题目主数据到 ES”是同一套设计思想：

- 检索友好的静态字段放 ES
- 高频变化、关系性强的数据继续留在 DB

所以你简历里如果想讲完整一点，可以把这两句话连起来：

“题目主数据通过定时任务同步到 ES，关系类高频变化数据继续留在 MySQL 查询，实现动静分离，在控制同步成本的同时保证检索性能。”

---

## 八、这套方案的优点

### 1. 实现复杂度适中

相比：

- binlog CDC
- MQ 双写
- 分布式事务

这种“Scheduler + 增量窗口 + 回库补全”的实现更简单，也更适合中小规模项目。

### 2. 具备幂等性

同一条题目多次同步不会重复写出多条 ES 文档，因为主键一致。

### 3. 能处理逻辑删除

由于同步 SQL 不过滤 `isDelete`，删除状态也能被传递到 ES。

### 4. 最终一致性更稳

查询时回 MySQL 取最新数据，还会顺手清理 ES 脏数据。

### 5. 兼顾性能和维护成本

ES 做全文检索，MySQL 保留真实数据，职责分工比较清晰。

---

## 九、这套方案的边界

这部分面试里最好也主动说明。

### 1. 它不是强一致

因为同步是定时任务触发，所以天然存在短暂延迟。  
这是一种“最终一致性”，不是“事务级实时一致性”。

### 2. 存在时间窗口重复扫描

回看 5 分钟会重复扫到一些数据。  
不过由于使用相同 `id` 覆盖写入，所以这个重复是可接受的。

### 3. 高并发下仍依赖 MySQL 回库

ES 搜索后还要回 MySQL 补全最新实体，这会保留一部分数据库压力。  
但它换来了更好的准确性和一致性。

---

## 十、怎么用面试话术讲这段设计

可以这样讲：

“项目里题目搜索采用了 MySQL 到 Elasticsearch 的定时同步方案。具体做法是通过 Spring Scheduler 每分钟执行一次增量同步任务，按 `updateTime` 回看最近 5 分钟的题目变更数据，包括新增、编辑和逻辑删除，再转换成 ES 文档批量写入。为了保证更新准确性，我让 ES 文档主键直接复用数据库题目表的 `id`，这样相同题目多次同步时会覆盖更新而不是重复插入，整个同步链路具备幂等性。与此同时，查询侧采用了动静分离设计，ES 负责全文检索和相关性召回，最终返回前再按命中 id 回 MySQL 查询最新题目数据，并顺手清理 ES 脏文档，从而在保证检索效率的同时兼顾数据一致性。” 

---

## 十一、一句话总结

这套代码的本质是：

- 用 `@Scheduled` 做 MySQL -> ES 的增量同步
- 用数据库主键复用为 ES 文档主键，保证覆盖更新和幂等性
- 用“ES 召回 + MySQL 回库补全”实现检索效率和最终一致性的平衡

它不是最重的架构方案，但对于这个项目的体量和场景来说，设计是合理且落地清晰的。
