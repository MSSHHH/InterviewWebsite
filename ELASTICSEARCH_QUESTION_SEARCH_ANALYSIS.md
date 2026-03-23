# Elasticsearch 题目检索体系代码总结

## 1. 这句项目描述，对应的代码到底在哪

项目描述：

> 搭建 Elasticsearch 检索体系替代 MySQL 模糊查询，通过绑定 IK 分词器提升分词搜索能力与查询性能，并采用动静分离策略构建题目索引，降低 ES 同步更新成本。

对应代码主要在这些位置：

- 检索入口与降级逻辑
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/controller/QuestionController.java`
- MySQL 模糊查询兜底实现
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java`
- Elasticsearch 查询主逻辑
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java`
- ES 文档模型
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java`
- ES 索引 Mapping
  - `mianshiya-next-backend/sql/question_es_mapping.json`
- ES 增量 / 全量同步任务
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/cycle/IncSyncQuestionToEs.java`
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/job/once/FullSyncQuestionToEs.java`
- 增量同步时用于查最近变更题目的 Mapper
  - `mianshiya-next-backend/src/main/java/com/yupi/mianshiya/mapper/QuestionMapper.java`
- ES 连接配置
  - `mianshiya-next-backend/src/main/resources/application.yml`
- 前端触发检索请求的页面组件
  - `mianshiya-next-frontend/src/components/QuestionTable/index.tsx`

## 2. 先给结论：这个项目是怎么做题目搜索的

### 2.1 搜索链路

整体链路是：

1. 前端题目搜索页发起 `/api/question/search/page/vo`
2. Controller 优先调用 `questionService.searchFromEs(...)`
3. `searchFromEs(...)` 在 ES 中完成全文检索、标签过滤、题库过滤、分页、排序
4. ES 只负责召回题目 id 和静态检索字段
5. 再回 MySQL 按 id 批量查最新题目数据
6. 最终封装成 `QuestionVO` 返回前端
7. 如果 ES 不可用，则降级走 MySQL `like` 查询

这个设计不是“彻底抛弃 MySQL”，而是：

- 正常情况下：ES 作为主搜索引擎
- 异常情况下：MySQL 作为兜底链路

### 2.2 为什么要这么做

因为题目搜索属于典型的全文检索场景：

- 需要按标题、内容、答案做模糊匹配
- 中文检索需要分词
- 数据量增大后，MySQL 的 `%like%` 性能和相关性排序都不理想

所以这里把“搜索能力”交给了 ES，把“最终权威数据”仍然留在 MySQL。

## 3. “替代 MySQL 模糊查询”在代码里是怎么体现的

### 3.1 MySQL 原始方案仍然保留，但只做兜底

在 `QuestionServiceImpl#getQueryWrapper` 中，原始 MySQL 模糊查询逻辑还在：

```java
if (StringUtils.isNotBlank(searchText)) {
    queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
}
queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
```

这段代码的位置：

- `QuestionServiceImpl.java:122-145`

它说明两件事：

1. 这个项目最初是 MySQL `like` 模糊查询方案
2. 现在没有完全删除，而是保留作 ES 故障时的 fallback

### 3.2 Controller 层显式把 ES 作为主链路

真正让 ES 成为主链路的是：

```java
try {
    questionPage = questionService.searchFromEs(questionQueryRequest);
} catch (Exception e) {
    log.error("search from es failed, fallback to mysql", e);
    questionPage = questionService.listQuestionByPage(questionQueryRequest);
}
```

位置：

- `QuestionController.java:368-387`

这段代码表达得非常清楚：

- 先走 ES
- ES 失败时降级到 MySQL

所以更准确的项目表述应该是：

> 用 Elasticsearch 承担题目检索主链路，替代 MySQL 作为默认模糊搜索方案，并保留 MySQL 查询作为降级兜底。

## 4. IK 分词器是怎么绑定进去的

### 4.1 绑定位置 1：索引 Mapping

在 `sql/question_es_mapping.json` 中，`title`、`content`、`answer` 三个全文字段都绑定了 IK：

```json
"title": {
  "type": "text",
  "analyzer": "ik_max_word",
  "search_analyzer": "ik_smart"
}
```

位置：

- `question_es_mapping.json:10-30`

这表示：

- 建索引时用 `ik_max_word`
- 查询时用 `ik_smart`

### 4.2 绑定位置 2：Java 文档模型注解

在 `QuestionEsDTO` 上也做了同样声明：

```java
@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private String title;
```

位置：

- `QuestionEsDTO.java:36-54`

### 4.3 为什么索引和查询用不同分词器

这里是 ES 中文搜索里的经典搭配：

- `ik_max_word`
  - 切词更细
  - 召回更强
  - 适合建索引

- `ik_smart`
  - 切词更粗
  - 噪声更少
  - 适合查询

比如用户搜“数据库索引”：

- 索引阶段细粒度切分，能覆盖更多词项
- 查询阶段较稳，不容易匹配出太多无关结果

### 4.4 这里有一个很重要的事实

代码里**没有“安装 IK 插件”的代码**，只有“绑定 IK 分词器”的配置。

也就是说：

- IK 插件本身需要提前装到 Elasticsearch 实例里
- 项目代码负责告诉 ES 哪些字段使用 IK

这也是为什么 README 里要求你先手动创建索引 Mapping。

## 5. ES 文档模型为什么不是直接复用数据库实体

### 5.1 `QuestionEsDTO` 是专门的检索模型

`QuestionEsDTO` 上的注释已经把设计意图说得很清楚：

- 只放检索相关字段
- 文本字段绑定 IK
- 通过 id 与数据库实体一一对应

位置：

- `QuestionEsDTO.java:18-25`

它包含这些字段：

- `id`
- `title`
- `content`
- `answer`
- `tags`
- `userId`
- `createTime`
- `updateTime`
- `isDelete`

### 5.2 为什么不直接把整张题目表所有字段都塞进去

因为 ES 索引不是数据库镜像表，而是“检索视图”。

只放检索必要字段的好处：

1. 文档更轻
2. 索引更新成本更低
3. ES 只承担“检索”职责，不承担完整业务对象存储
4. 高变字段不用每次都跟着 ES 重建索引

### 5.3 DTO 转换做了什么

在 `QuestionEsDTO.objToDto(question)` 里：

- 复制基础字段
- 把数据库中的 `tags` JSON 字符串反序列化成 `List<String>`

位置：

- `QuestionEsDTO.java:96-106`

为什么要这样做？

因为数据库里 `tags` 是 JSON 字符串，而 ES 里 `tags` 被定义成 `keyword` 数组，更适合 `termQuery` 精确过滤。

## 6. ES 查询主逻辑是怎么写的

核心在：

- `QuestionServiceImpl.java:257-355`

### 6.1 先取查询参数

包括：

- `id`
- `notId`
- `searchText`
- `tags`
- `questionBankId`
- `userId`
- 分页参数
- 排序参数

### 6.2 用 `BoolQueryBuilder` 组装组合查询

代码：

```java
BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
```

然后逐步拼条件：

- `filter(termQuery("isDelete", 0))`
- `filter(termQuery("id", id))`
- `mustNot(termQuery("id", notId))`
- `filter(termQuery("userId", userId))`

这里大量使用 `filter` 而不是 `must`，是合理的，因为：

- `filter` 不参与打分
- 更适合精确过滤条件
- 性能通常更稳定

### 6.3 全文检索条件怎么写

搜索词会同时匹配：

- `title`
- `content`
- `answer`

代码：

```java
boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
boolQueryBuilder.minimumShouldMatch(1);
```

位置：

- `QuestionServiceImpl.java:305-310`

含义是：

- 三个字段中命中任意一个即可召回
- 这比 MySQL 只 `like title/content` 更强

### 6.4 标签过滤怎么写

代码：

```java
for (String tag : tags) {
    boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
}
```

位置：

- `QuestionServiceImpl.java:299-303`

这表示：

- 每个 tag 都必须命中
- 语义上相当于多标签 AND 过滤

### 6.5 排序策略

默认：

- 按相关性分数排序

如果前端传了排序字段：

- 改为字段排序

代码：

```java
SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
if (StringUtils.isNotBlank(sortField)) {
    sortBuilder = SortBuilders.fieldSort(sortField);
    sortBuilder.order(...);
}
```

位置：

- `QuestionServiceImpl.java:312-318`

## 7. “动静分离策略”到底是什么意思

这是这套实现里最值得讲的地方。

### 7.1 什么是“静”

适合放在 ES 中、变化没那么频繁、且主要用于搜索召回的字段：

- `title`
- `content`
- `answer`
- `tags`
- `userId`
- `createTime`
- `updateTime`
- `isDelete`

这些字段组成了 `QuestionEsDTO`。

### 7.2 什么是“动”

这里最典型的动态数据不是题目本身，而是：

- 题库和题目之间的关系表 `question_bank_question`

因为这类关系可能经常调整：

- 题目加入题库
- 题目移出题库
- 一个题目在多个题库之间变动

如果把这类关系直接冗余进 ES 文档，会带来两个问题：

1. 关系一变，ES 文档就要频繁更新
2. 索引维护成本明显升高

### 7.3 代码里怎么体现“动静分离”

最关键的一段：

```java
if (questionBankId != null) {
    List<Long> questionIdList = questionBankQuestionService.listObjs(...);
    if (CollUtil.isEmpty(questionIdList)) {
        return new Page<>(..., 0);
    }
    boolQueryBuilder.filter(QueryBuilders.termsQuery("id", questionIdList));
}
```

位置：

- `QuestionServiceImpl.java:285-298`

意思是：

1. 先去 MySQL 关系表查出这个题库下有哪些题目 id
2. 再把这些 id 作为 `termsQuery` 条件交给 ES

所以这里不是“把题库关系提前建到 ES 里搜索”，而是：

- 动态关系留在 DB
- 静态检索字段放在 ES
- 查询时现场拼接

### 7.4 返回结果时为什么还要回 MySQL

这段是第二层动静分离：

```java
List<Long> questionIdList = searchHits.getSearchHits().stream()
        .map(searchHit -> searchHit.getContent().getId())
        .collect(Collectors.toList());
List<Question> questionList = this.listByIds(questionIdList);
```

位置：

- `QuestionServiceImpl.java:332-353`

它的设计思想是：

- ES 负责“找到哪些题目”
- MySQL 负责“返回这些题目的最新权威数据”

这样做的好处：

1. ES 不需要完全承担主数据存储职责
2. 即使 ES 同步有轻微延迟，最终返回的数据仍然以数据库为准
3. 某些高频变更字段不用追求对 ES 的强一致

### 7.5 这里还有一个细节

回库后，代码会按 ES 命中顺序重新组装：

```java
for (Long questionId : questionIdList) {
    Question latestQuestion = questionIdQuestionMap.get(questionId);
    ...
}
```

位置：

- `QuestionServiceImpl.java:341-352`

原因是 `listByIds` 不保证顺序，而搜索结果顺序通常很重要。

## 8. ES 同步更新成本为什么会降低

### 8.1 因为不是所有数据都塞进 ES

如果把题库关系、用户展示信息等都冗余进 ES，那么：

- 每次题库关系变动
- 每次用户信息变化
- 每次题目局部字段变化

都可能触发索引重建或文档更新。

而现在这套设计：

- ES 只存检索友好字段
- 高变关系放 DB
- 返回前回库补齐最新数据

所以 ES 的更新触发面被明显缩小了。

### 8.2 同步任务也做得比较克制

#### 增量同步

`IncSyncQuestionToEs` 默认开启，每分钟执行一次：

- `IncSyncQuestionToEs.java:23-67`

策略是：

1. 回看最近 5 分钟 `updateTime` 发生变化的数据
2. 包含逻辑删除数据
3. 转为 `QuestionEsDTO`
4. 分批 `saveAll`

关键点：

- 调度频率：1 分钟
- 回看窗口：5 分钟
- 幂等覆盖：ES 文档 id = DB id

这种“1 分钟调度 + 5 分钟回看窗口”的做法很实用，可以容忍调度抖动和短暂故障。

#### 全量同步

`FullSyncQuestionToEs` 用于初始化：

- `FullSyncQuestionToEs.java:22-55`

默认没有启用 `@Component`，需要手动开启一次。

这意味着：

- 首次建索引时可全量灌库
- 日常运行靠增量同步维护

### 8.3 删除状态怎么同步

Mapper 里专门有：

```java
@Select("select * from question where updateTime >= #{minUpdateTime}")
List<Question> listQuestionWithDelete(Date minUpdateTime);
```

位置：

- `QuestionMapper.java:18-26`

注意这里**故意不加 `isDelete = 0`**，就是为了把逻辑删除状态也同步到 ES。

然后 ES 查询时再统一过滤：

```java
boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
```

这样可以保证：

- ES 中的删除状态是最新的
- 检索结果又不会把已删除数据放出来

## 9. 前端是怎么触发这套搜索体系的

前端题目页用的是：

- `QuestionTable/index.tsx`

它会在表格请求里调用：

```ts
searchQuestionVoByPageUsingPost({
  ...params,
  sortField,
  sortOrder,
  ...filter,
})
```

位置：

- `QuestionTable/index.tsx:88-120`

所以前端只负责传：

- 搜索词
- 分页参数
- 排序字段
- 标签过滤

真正的 ES / MySQL 分流逻辑都在后端 Controller 和 Service 里。

## 10. 这套实现的优点

### 10.1 搜索能力比 MySQL `%like%` 强

- 支持中文分词
- 支持多字段全文检索
- 支持相关性排序

### 10.2 性能更适合全文检索

- ES 更适合倒排索引检索
- 不依赖数据库全文模糊扫描

### 10.3 架构更稳

- ES 是主链路
- MySQL 是兜底
- 不会因为 ES 故障直接把搜索功能打死

### 10.4 同步成本更低

- 不把高频关系表直接塞进 ES
- 通过“ES 召回 + DB 回库”降低索引维护压力

## 11. 这套实现的边界和注意点

### 11.1 不是强一致

因为 ES 走的是定时增量同步，不是实时 binlog 同步，所以：

- 题目刚修改后
- 短时间内 ES 可能还没同步到最新索引内容

但由于最终结果回了 MySQL，所以：

- 结果内容本身尽量保持新
- 只是“能不能被搜到”会有短暂延迟窗口

### 11.2 MySQL 回库会多一次查询

这是动静分离的代价：

- ES 先查一次
- DB 再回一次

但换来的好处是：

- 降低 ES 文档冗余
- 降低同步复杂度
- 提高数据新鲜度

### 11.3 “IK 提升性能”要分开说

更严谨地说：

- IK 主要提升的是**中文分词召回质量**
- ES 本身替代 MySQL `like`，提升的是**全文检索性能**

所以面试时最好不要把“IK”和“性能提升”完全混成一件事。

更准确的表达是：

> 通过 Elasticsearch 倒排索引提升全文搜索性能，通过 IK 分词器提升中文搜索的召回和匹配效果。

## 12. 一版更准确的项目表述

如果你想让简历表述更贴近代码，我建议改成：

> 面向题目搜索场景，搭建 Elasticsearch 检索主链路替代 MySQL 默认模糊查询，基于 IK 分词器增强标题、内容、答案等字段的中文检索能力；同时采用“ES 存静态检索字段 + MySQL 维护动态关系与权威数据”的动静分离策略，并结合全量初始化、定时增量同步和 MySQL 降级兜底机制，在保证搜索性能的同时降低索引同步维护成本。

## 13. 面试时可以怎么讲

可以按这个顺序说：

1. 原来题目搜索是 MySQL `like`，中文全文检索能力和性能都一般。
2. 我把搜索主链路切到 Elasticsearch，搜索接口优先走 ES，异常时自动降级回 MySQL。
3. ES 索引里只放题目标题、内容、答案、标签等检索友好字段，并给文本字段绑定 `ik_max_word` / `ik_smart`，提升中文搜索的召回效果。
4. 对于题库和题目关系这种高频变更数据，没有直接冗余进 ES，而是查询时先查关系表拿题目 id，再用 `termsQuery` 过滤 ES，这就是动静分离。
5. ES 搜索命中后再按 id 回 MySQL 取最新题目数据，既降低索引同步成本，又保证返回结果尽量以数据库为准。
6. 同步方面，首次用全量任务初始化索引，日常靠每分钟增量同步最近 5 分钟变更，兼顾一致性和维护成本。

## 14. 关键代码定位速查

- ES 配置
  - `application.yml:101-105`
- 题目索引 Mapping
  - `sql/question_es_mapping.json:1-50`
- ES 文档模型
  - `QuestionEsDTO.java:23-127`
- MySQL 模糊查询兜底
  - `QuestionServiceImpl.java:105-147`
- ES 查询主逻辑
  - `QuestionServiceImpl.java:257-355`
- 题库关系的动静分离过滤
  - `QuestionServiceImpl.java:285-298`
- ES 命中后回库补最新数据
  - `QuestionServiceImpl.java:332-353`
- 搜索接口 ES -> MySQL 降级
  - `QuestionController.java:368-387`
- 增量同步
  - `IncSyncQuestionToEs.java:23-68`
- 全量同步
  - `FullSyncQuestionToEs.java:22-55`
- 查询最近变更题目（含逻辑删除）
  - `QuestionMapper.java:18-26`
- 前端搜索请求入口
  - `QuestionTable/index.tsx:88-120`

