use mianshiya;

-- 2026-03-22 补充一批更贴近当前项目技术栈的 mock 数据
-- 设计目标：
-- 1) 数据语义真实，围绕 Spring Boot / Redis / ES / 微服务治理；
-- 2) 可重复执行，避免反复导入产生重复记录；
-- 3) 同时补齐题库、题目和题库题目关联。

-- 使用当前本地管理员用户作为创建者；若不存在则退化为默认管理员 5
SET @seed_user_id := (
    SELECT COALESCE(
        (SELECT id FROM user WHERE userAccount = 'mhhh' LIMIT 1),
        5
    )
);

-- ----------------------------
-- 题库
-- ----------------------------
INSERT INTO question_bank (title, description, picture, userId)
SELECT 'Spring Boot 实战', '覆盖自动配置、IOC、事务与项目落地中的常见面试问题',
       'https://www.mianshiya.com/logo.png', @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE title = 'Spring Boot 实战'
);

INSERT INTO question_bank (title, description, picture, userId)
SELECT 'Redis 高并发', '聚焦缓存设计、BitMap、Lua 脚本与高并发场景下的数据一致性',
       'https://www.mianshiya.com/logo.png', @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE title = 'Redis 高并发'
);

INSERT INTO question_bank (title, description, picture, userId)
SELECT 'Elasticsearch 检索', '围绕倒排索引、中文分词、数据同步和检索优化的核心题目',
       'https://www.mianshiya.com/logo.png', @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE title = 'Elasticsearch 检索'
);

INSERT INTO question_bank (title, description, picture, userId)
SELECT '微服务治理', '聚焦 Sentinel、Nacos、限流降级与服务治理的常见面试问题',
       'https://www.mianshiya.com/logo.png', @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank WHERE title = '微服务治理'
);

-- ----------------------------
-- 题目
-- ----------------------------
INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Spring Boot 自动配置原理',
    '请解释 Spring Boot 自动配置的核心机制，以及 @EnableAutoConfiguration、spring.factories（或新版自动配置导入文件）在其中分别扮演什么角色。',
    '["Spring Boot","自动配置","Java"]',
    'Spring Boot 自动配置的本质是按条件装配 Bean。启动类上的 @SpringBootApplication 间接开启自动配置，框架会加载自动配置类清单，再通过 @ConditionalOnClass、@ConditionalOnMissingBean 等条件判断是否生效，从而做到约定优于配置。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Spring Boot 自动配置原理'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Spring Bean 生命周期',
    '一个 Spring Bean 从实例化到销毁会经历哪些阶段？如果要在初始化前后扩展逻辑，通常有哪些方式？',
    '["Spring","Bean","生命周期"]',
    'Bean 生命周期通常包括实例化、属性注入、Aware 回调、BeanPostProcessor 前置处理、初始化方法、BeanPostProcessor 后置处理、使用以及销毁。常见扩展点包括 InitializingBean、@PostConstruct、自定义 init-method 和 BeanPostProcessor。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Spring Bean 生命周期'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Spring 事务失效的常见场景',
    '请列举几个 Spring 声明式事务不生效的常见场景，并说明背后的原因。',
    '["Spring","事务","AOP"]',
    '典型场景包括方法不是 public、同类内部自调用、异常被吞掉、未抛出受事务管理器感知的异常以及数据库引擎不支持事务。根本原因通常是事务依赖 AOP 代理，而代理没有被正确触发或事务边界被破坏。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Spring 事务失效的常见场景'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Redis 缓存击穿、穿透、雪崩的区别',
    '请分别解释缓存击穿、缓存穿透和缓存雪崩，并给出常见的治理方案。',
    '["Redis","缓存","高并发"]',
    '缓存穿透是查询不存在的数据导致请求直接打到数据库；缓存击穿是热点 key 失效瞬间大量请求击中数据库；缓存雪崩是大量 key 同时失效。常见方案包括布隆过滤器、热点数据不过期、互斥锁、随机过期时间、降级兜底和多级缓存。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Redis 缓存击穿、穿透、雪崩的区别'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Redis BitMap 适合哪些业务场景',
    'Redis BitMap 的底层特点是什么？它适合解决哪些业务问题，使用时要注意什么？',
    '["Redis","BitMap","数据结构"]',
    'BitMap 适合表示海量布尔状态，比如签到、在线状态、活跃统计、布尔特征标记等。它空间利用率高，但更适合连续整数下标场景；如果对象 id 稀疏或需要存复杂属性，就不适合直接使用 BitMap。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Redis BitMap 适合哪些业务场景'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Lua 脚本在 Redis 中的作用',
    '为什么在 Redis 中经常使用 Lua 脚本？它相比客户端多次往返执行命令有什么优势？',
    '["Redis","Lua","原子性"]',
    'Lua 脚本可以把多条 Redis 命令放在服务端一次性执行，具备原子性，减少网络往返，适合做限流计数、库存扣减、分布式锁校验等需要“读写组合且不能被打断”的场景。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Lua 脚本在 Redis 中的作用'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Elasticsearch 倒排索引原理',
    '什么是倒排索引？为什么 Elasticsearch 在全文检索场景下通常比 MySQL 模糊查询更高效？',
    '["Elasticsearch","倒排索引","搜索"]',
    '倒排索引会为词项建立文档列表，查询时直接根据词找到命中文档，而不是像 MySQL 的前后模糊查询那样对大量文本逐行扫描，因此在大规模文本检索场景下通常更高效。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Elasticsearch 倒排索引原理'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'IK 分词器在中文搜索中的作用',
    '为什么 Elasticsearch 做中文搜索时常常要接入 IK 分词器？ik_max_word 和 ik_smart 一般分别在什么阶段使用？',
    '["Elasticsearch","IK","中文搜索"]',
    '中文文本没有天然空格分词，默认分词效果有限，因此常接入 IK。通常索引阶段使用 ik_max_word 提高召回，查询阶段使用 ik_smart 降低噪声，这样能在召回率和精确度之间取得平衡。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'IK 分词器在中文搜索中的作用'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    '如何设计 MySQL 到 ES 的数据同步',
    '如果业务数据存储在 MySQL，而搜索走 Elasticsearch，一般如何设计两边的数据同步？为什么经常说这是一种最终一致性方案？',
    '["Elasticsearch","MySQL","数据同步"]',
    '常见方案包括定时任务同步、消息队列异步同步和基于 binlog 的 CDC 同步。因为搜索索引往往不是与数据库写入处于同一个事务里，所以通常保证的是最终一致性而不是强一致性，需要在性能、复杂度和一致性之间做权衡。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = '如何设计 MySQL 到 ES 的数据同步'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Sentinel 限流和熔断的区别',
    '在微服务场景下，Sentinel 的限流和熔断降级分别解决什么问题？两者的触发依据有什么不同？',
    '["Sentinel","限流","熔断"]',
    '限流关注的是瞬时流量保护，防止请求过多压垮系统；熔断降级关注的是异常率、慢调用等服务质量问题，在服务不稳定时临时停止放量请求。前者核心是控制流量，后者核心是保护依赖和快速失败。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Sentinel 限流和熔断的区别'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    'Nacos 在微服务中的作用',
    '请说明 Nacos 在微服务体系里通常承担哪些职责，服务注册发现和配置中心分别解决什么问题？',
    '["Nacos","微服务","配置中心"]',
    'Nacos 通常承担服务注册发现和配置管理两类职责。注册发现解决服务实例动态上下线后的寻址问题，配置中心解决多环境配置统一管理、动态推送和集中治理问题。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = 'Nacos 在微服务中的作用'
);

INSERT INTO question (title, content, tags, answer, userId)
SELECT
    '如何设计一个防止数据库被打爆的查询链路',
    '如果某个热门接口在高并发下容易把数据库打满，你会如何设计缓存、限流、降级和热点探测策略？',
    '["高并发","缓存","系统设计"]',
    '可以通过多级缓存降低读压力，用热点探测识别高频 key，用限流和熔断保护下游，用本地缓存或空结果作为降级兜底，并通过异步更新、互斥锁和随机过期时间减少缓存击穿与雪崩风险。',
    @seed_user_id
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM question WHERE title = '如何设计一个防止数据库被打爆的查询链路'
);

-- ----------------------------
-- 题库题目关联
-- ----------------------------
INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Spring Boot 自动配置原理'
WHERE qb.title = 'Spring Boot 实战'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Spring Bean 生命周期'
WHERE qb.title = 'Spring Boot 实战'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Spring 事务失效的常见场景'
WHERE qb.title = 'Spring Boot 实战'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Redis 缓存击穿、穿透、雪崩的区别'
WHERE qb.title = 'Redis 高并发'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Redis BitMap 适合哪些业务场景'
WHERE qb.title = 'Redis 高并发'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Lua 脚本在 Redis 中的作用'
WHERE qb.title = 'Redis 高并发'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Elasticsearch 倒排索引原理'
WHERE qb.title = 'Elasticsearch 检索'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'IK 分词器在中文搜索中的作用'
WHERE qb.title = 'Elasticsearch 检索'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = '如何设计 MySQL 到 ES 的数据同步'
WHERE qb.title = 'Elasticsearch 检索'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Sentinel 限流和熔断的区别'
WHERE qb.title = '微服务治理'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = 'Nacos 在微服务中的作用'
WHERE qb.title = '微服务治理'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );

INSERT INTO question_bank_question (questionBankId, questionId, userId)
SELECT qb.id, q.id, @seed_user_id
FROM question_bank qb
JOIN question q ON q.title = '如何设计一个防止数据库被打爆的查询链路'
WHERE qb.title = '微服务治理'
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_question rel
      WHERE rel.questionBankId = qb.id AND rel.questionId = q.id
  );
