from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Preformatted, PageBreak
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from xml.sax.saxutils import escape
from datetime import datetime

# Register Chinese font
pdfmetrics.registerFont(UnicodeCIDFont('STSong-Light'))

output_path = 'output/pdf/面试刷题平台_技术点代码讲解报告.pdf'

doc = SimpleDocTemplate(
    output_path,
    pagesize=A4,
    leftMargin=18 * mm,
    rightMargin=18 * mm,
    topMargin=18 * mm,
    bottomMargin=18 * mm,
)

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name='TitleCN',
    parent=styles['Title'],
    fontName='STSong-Light',
    fontSize=22,
    leading=30,
    alignment=TA_CENTER,
    textColor=colors.HexColor('#1f2937'),
))
styles.add(ParagraphStyle(
    name='SubTitleCN',
    parent=styles['Normal'],
    fontName='STSong-Light',
    fontSize=11,
    leading=18,
    alignment=TA_CENTER,
    textColor=colors.HexColor('#4b5563'),
))
styles.add(ParagraphStyle(
    name='H1CN',
    parent=styles['Heading1'],
    fontName='STSong-Light',
    fontSize=16,
    leading=24,
    textColor=colors.HexColor('#111827'),
    spaceBefore=12,
    spaceAfter=8,
))
styles.add(ParagraphStyle(
    name='H2CN',
    parent=styles['Heading2'],
    fontName='STSong-Light',
    fontSize=13,
    leading=20,
    textColor=colors.HexColor('#111827'),
    spaceBefore=8,
    spaceAfter=6,
))
styles.add(ParagraphStyle(
    name='BodyCN',
    parent=styles['Normal'],
    fontName='STSong-Light',
    fontSize=10.5,
    leading=18,
    textColor=colors.HexColor('#111827'),
))
styles.add(ParagraphStyle(
    name='NoteCN',
    parent=styles['Normal'],
    fontName='STSong-Light',
    fontSize=9.5,
    leading=16,
    textColor=colors.HexColor('#6b7280'),
))
styles.add(ParagraphStyle(
    name='CodeCN',
    parent=styles['Code'],
    fontName='Courier',
    fontSize=8.3,
    leading=11,
    backColor=colors.HexColor('#f3f4f6'),
    borderPadding=6,
    borderWidth=0.5,
    borderColor=colors.HexColor('#d1d5db'),
))


def p(text, style='BodyCN'):
    return Paragraph(escape(text), styles[style])


def bullet(text):
    return Paragraph('- ' + escape(text), styles['BodyCN'])


def code_block(text):
    return Preformatted(text, styles['CodeCN'])


def section_title(text):
    return Paragraph(escape(text), styles['H1CN'])


def sub_title(text):
    return Paragraph(escape(text), styles['H2CN'])


def add_page_number(canvas, doc_obj):
    canvas.saveState()
    canvas.setFont('Helvetica', 9)
    canvas.setFillColor(colors.HexColor('#6b7280'))
    page_num = canvas.getPageNumber()
    canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, f'Page {page_num}')
    canvas.restoreState()


story = []

# Cover
story.append(Paragraph('面试刷题平台技术点代码讲解报告', styles['TitleCN']))
story.append(Spacer(1, 8))
story.append(Paragraph('基于 Spring Boot + Redis + MySQL + Elasticsearch + Sentinel + HotKey', styles['SubTitleCN']))
story.append(Spacer(1, 6))
story.append(Paragraph('用途：面试逐点复述 / 技术细节追问应答 / 代码依据对照', styles['SubTitleCN']))
story.append(Spacer(1, 24))
story.append(Paragraph(f'生成时间：{datetime.now().strftime("%Y-%m-%d %H:%M")}', styles['SubTitleCN']))
story.append(Spacer(1, 3))
story.append(Paragraph('代码仓库：InterviewWebsite / mianshiya-next-backend', styles['SubTitleCN']))
story.append(Spacer(1, 24))
story.append(p('说明：本报告严格以当前仓库代码为依据生成。对“简历描述”和“当前代码状态”不一致的地方，会明确标注，帮助你在面试时主动解释。', 'NoteCN'))
story.append(PageBreak())

# Readme
story.append(section_title('0. 阅读方式与面试策略'))
story.append(bullet('先讲“业务问题”，再讲“技术方案”，最后讲“代码落地 + 结果指标”。'))
story.append(bullet('每个技术点建议按 60~90 秒主讲 + 30 秒追问扩展。'))
story.append(bullet('如果面试官追问深度，优先拿“代码路径 + 关键方法 + 边界处理”来回答。'))
story.append(bullet('主动说明当前仓库中的教学版开关（如 TODO 注释、降级开关），体现你对工程落地的掌控。'))
story.append(Spacer(1, 10))
story.append(sub_title('本报告覆盖的 7 个简历技术点'))
story.append(bullet('1) ES 替代 MySQL 模糊查询 + IK 分词'))
story.append(bullet('2) 动静分离检索策略（高频字段回表）'))
story.append(bullet('3) Spring Scheduler 定时同步 MySQL -> ES'))
story.append(bullet('4) Redis BitMap + Redisson 用户年度刷题记录'))
story.append(bullet('5) Caffeine 本地缓存 + HotKey 热点探测'))
story.append(bullet('6) Sentinel 限流熔断 + fallback 兜底'))
story.append(bullet('7) Redis 频率统计 + Lua 原子计数 + 自动封禁'))
story.append(PageBreak())

# Section 1
story.append(section_title('1. Elasticsearch 检索替代 MySQL 模糊查询'))
story.append(sub_title('1.1 业务目标'))
story.append(p('题目检索场景中，MySQL 的 like 多字段模糊匹配在数据量增长后会明显变慢，且分词能力弱。目标是使用 ES 的倒排索引 + match 查询，提升检索性能与相关性。'))
story.append(sub_title('1.2 代码证据（核心文件）'))
for t in [
    'QuestionServiceImpl.searchFromEs：src/main/java/com/yupi/mianshiya/service/impl/QuestionServiceImpl.java:257-329',
    'QuestionController.searchQuestionVOByPage：src/main/java/com/yupi/mianshiya/controller/QuestionController.java:360-372',
    'QuestionEsDTO 索引实体：src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java:19-21',
    'ES mapping（IK 分词器）：sql/post_es_mapping.json:7-30',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('1.3 关键实现链路'))
for t in [
    '请求入参 QuestionQueryRequest 接收 searchText / tags / userId / sortField 等条件。',
    '服务层构建 BoolQueryBuilder：filter 负责精确条件，should + matchQuery 负责关键词召回。',
    'minimumShouldMatch(1) 保证 title/content/answer 至少一个字段命中。',
    '排序默认 scoreSort，可切换字段排序（ASC/DESC）。',
    'ES 结果转换为 QuestionEsDTO，再映射回 Question，复用原分页返回结构。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// QuestionServiceImpl.searchFromEs 核心片段
if (StringUtils.isNotBlank(searchText)) {
    boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
    boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
    boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
    boolQueryBuilder.minimumShouldMatch(1);
}
'''))
story.append(Spacer(1, 8))
story.append(sub_title('1.4 IK 分词配置点'))
story.append(p('在 sql/post_es_mapping.json 中，title/content/answer 使用 ik_max_word（索引时粗粒度切分）和 ik_smart（查询时更稳妥）。这正是“中文分词搜索”在工程上的关键。'))
story.append(Spacer(1, 6))
story.append(sub_title('1.5 面试可复述话术（60 秒版）'))
story.append(p('我把题目检索从 MySQL like 迁到 ES。服务层按业务条件拼 BoolQuery：filter 做精确过滤，match 做分词召回，score 排序保证相关性。索引 mapping 里给 title/content/answer 绑定了 IK 分词器，中文搜索体验会比数据库模糊匹配更自然。最终 ES 命中结果再转成 Question 对象，和原接口结构兼容，前端无感升级。'))
story.append(Spacer(1, 6))
story.append(sub_title('1.6 当前仓库状态说明（务必面试主动说）'))
story.append(bullet('QuestionController 中 ES 调用目前被注释，默认走 MySQL 作为降级路径（:366-370）。'))
story.append(bullet('这属于教学版“功能开关”设计：ES 不可用时系统仍可启动并提供基础查询能力。'))
story.append(PageBreak())

# Section 2
story.append(section_title('2. 动静分离检索策略（高频字段回表）'))
story.append(sub_title('2.1 业务目标'))
story.append(p('ES 适合存“检索字段”，但点赞/收藏等高频变化字段如果强同步到 ES，会增加写放大和一致性成本。目标是：ES 只负责召回，动态字段以 DB 为准。'))
story.append(sub_title('2.2 代码证据（当前仓库主要在 Post 模块体现）'))
for t in [
    'PostServiceImpl.searchFromEs：src/main/java/com/yupi/mianshiya/service/impl/PostServiceImpl.java:133-227',
    '动态字段定义：src/main/java/com/yupi/mianshiya/model/entity/Post.java:44-51',
    'ES DTO 字段：src/main/java/com/yupi/mianshiya/model/dto/post/PostEsDTO.java:53-60',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('2.3 关键实现链路'))
for t in [
    '先用 ES 查出命中的 postId 列表（高召回、低延迟）。',
    '再用 selectBatchIds(postIdList) 回 DB 拉取最新记录。',
    '按 ES 命中顺序重组结果，保证“相关性排序 + 动态字段新鲜度”。',
    '若 ES 有、DB 无（物理删除），会主动清理 ES 脏数据。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// PostServiceImpl.searchFromEs 关键片段
List<Long> postIdList = searchHitList.stream()
    .map(searchHit -> searchHit.getContent().getId())
    .collect(Collectors.toList());
List<Post> postList = baseMapper.selectBatchIds(postIdList);
// ...按命中顺序组装，缺失则删 ES 脏数据
'''))
story.append(Spacer(1, 8))
story.append(sub_title('2.4 面试可复述话术（60 秒版）'))
story.append(p('我做了“动静分离”：ES 只承担检索和排序，不强求承载高频变化计数字段。查询时先 ES 召回 ID，再批量回表拿最新数据，既有搜索性能，也能保证点赞/收藏等动态字段一致性。这个方案比“每次写都同步 ES”更稳，写入压力也更小。'))
story.append(Spacer(1, 6))
story.append(sub_title('2.5 你可以主动补充的工程点'))
for t in [
    '若数据量更大，可引入异步批量回表 + 小型本地缓存减少重复回表。',
    '可以对“回表缺失”的删除策略做异步队列化，避免查询线程阻塞。',
]:
    story.append(bullet(t))
story.append(PageBreak())

# Section 3
story.append(section_title('3. Spring Scheduler 定时同步 MySQL -> ES'))
story.append(sub_title('3.1 业务目标'))
story.append(p('检索走 ES，业务写入走 MySQL。需要一条稳定的数据同步链路，让 ES 最终一致。'))
story.append(sub_title('3.2 代码证据'))
for t in [
    '定时任务开关：src/main/java/com/yupi/mianshiya/MainApplication.java:21 (@EnableScheduling)',
    '增量同步：src/main/java/com/yupi/mianshiya/job/cycle/IncSyncPostToEs.java:35-56',
    '全量同步：src/main/java/com/yupi/mianshiya/job/once/FullSyncPostToEs.java:33-47',
    '增量 SQL：src/main/java/com/yupi/mianshiya/mapper/QuestionMapper.java:21-22',
    'ES 主键：src/main/java/com/yupi/mianshiya/model/dto/question/QuestionEsDTO.java:28-29 (@Id)',
    'ES DAO：src/main/java/com/yupi/mianshiya/esdao/QuestionEsDao.java:14',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('3.3 关键实现链路'))
for t in [
    '每分钟扫描近 5 分钟 updateTime 变更数据（包含逻辑删除数据）。',
    '对象转 QuestionEsDTO 后按 500 条分页批量 saveAll，提高吞吐。',
    '依赖 ES 文档 ID（业务主键）保证幂等覆盖，避免重复插入。',
    '冷启动或全量回补时执行 FullSync，线上常态跑 IncSync。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// IncSyncPostToEs
@Scheduled(fixedRate = 60 * 1000)
public void run() {
    Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
    List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
    questionEsDao.saveAll(questionEsDTOList.subList(i, end));
}
'''))
story.append(Spacer(1, 8))
story.append(sub_title('3.4 面试可复述话术（60 秒版）'))
story.append(p('我用 Spring Scheduler 做了 MySQL 到 ES 的最终一致性同步。增量任务按 updateTime 拉窗口数据，批量写 ES；ES 文档主键直接用业务 id，天然幂等。全量任务用于初始化或修复。这样业务主写链路不被 ES 绑定，系统复杂度和故障面都更可控。'))
story.append(Spacer(1, 6))
story.append(sub_title('3.5 当前仓库状态说明'))
story.append(bullet('仓库中同时存在 Question 同步任务（被注释）与 Post 命名任务（启用）两套教学代码，面试时可解释为阶段演进痕迹。'))
story.append(PageBreak())

# Section 4
story.append(section_title('4. Redis BitMap + Redisson 用户年度刷题记录'))
story.append(sub_title('4.1 业务目标'))
story.append(p('用户签到/刷题日历属于“按天布尔状态”数据，若按行存数据库会产生大量记录和 IO。BitMap 更节省空间，天然适合年度日历。'))
story.append(sub_title('4.2 代码证据'))
for t in [
    '签到写入：src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:308-321',
    '签到查询：src/main/java/com/yupi/mianshiya/service/impl/UserServiceImpl.java:332-351',
    'Redis Key 规范：src/main/java/com/yupi/mianshiya/constant/RedisConstant.java:11-21',
    '接口入口：src/main/java/com/yupi/mianshiya/controller/UserController.java:341-362',
    'Redisson 客户端配置：src/main/java/com/yupi/mianshiya/config/RedissonConfig.java:27-35',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('4.3 关键实现链路'))
for t in [
    '以 year + userId 生成年度 BitMap key。',
    'dayOfYear 作为 offset，签到即 set(offset, true)。',
    '查询时一次性 asBitSet 拉到内存，用 nextSetBit 扫描所有已签到日期。',
    '前端可以直接渲染刷题记录日历（仅传已签到 day 列表）。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// UserServiceImpl.addUserSignIn
LocalDate date = LocalDate.now();
String key = RedisConstant.getUserSignInRedisKey(date.getYear(), userId);
RBitSet signInBitSet = redissonClient.getBitSet(key);
int offset = date.getDayOfYear();
if (!signInBitSet.get(offset)) {
    signInBitSet.set(offset, true);
}
'''))
story.append(Spacer(1, 8))
story.append(sub_title('4.4 面试可复述话术（60 秒版）'))
story.append(p('我把年度刷题记录抽象成 365 位布尔数组，用 Redis BitMap 存储。写入时按 dayOfYear 定位 bit 位，查询时把 bitmap 转 BitSet 一次扫描出所有命中日期。相比关系型逐条记录，这个模型在存储和读取上都更轻，特别适合日历类展示。'))
story.append(Spacer(1, 6))
story.append(sub_title('4.5 可被追问的细节'))
for t in [
    '闰年处理：dayOfYear 自动到 366。',
    '并发写同一天是幂等 set true。',
    '若要跨年统计，可批量拉多年的 key 做 union 计算。',
]:
    story.append(bullet(t))
story.append(PageBreak())

# Section 5
story.append(section_title('5. Caffeine 本地缓存 + HotKey 热点探测'))
story.append(sub_title('5.1 业务目标'))
story.append(p('题库详情是高频读接口，热点流量集中在少量 key。目标是用 HotKey 自动识别热点，命中后走本地缓存，减少数据库压力。'))
story.append(sub_title('5.2 代码证据'))
for t in [
    'HotKey 客户端初始化：src/main/java/com/yupi/mianshiya/config/HotKeyConfig.java:43-52',
    '本地缓存容量参数（Caffeine）：src/main/java/com/yupi/mianshiya/config/HotKeyConfig.java:33',
    '配置项：src/main/resources/application.yml:130-134',
    '题库详情热点判断：src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:154-164',
    '回源后回填缓存：src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:185-187',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('5.3 关键实现链路'))
for t in [
    '接口先生成稳定 key（bank_detail_{id}）。',
    'JdHotKeyStore.isHotKey(key) 判断是否已被探测为热点。',
    '热点且本地有值：直接返回；否则查库并 smartSet 回填。',
    'HotKey 客户端内部使用本地 Caffeine 容器承载热点缓存。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// QuestionBankController.getQuestionBankVOById
String key = "bank_detail_" + id;
if (JdHotKeyStore.isHotKey(key)) {
    Object cached = JdHotKeyStore.get(key);
    if (cached != null) {
        return ResultUtils.success((QuestionBankVO) cached);
    }
}
// DB 查询后
JdHotKeyStore.smartSet(key, questionBankVO);
'''))
story.append(Spacer(1, 8))
story.append(sub_title('5.4 面试可复述话术（60 秒版）'))
story.append(p('我在题库详情接口接了 HotKey。请求先判断 key 是否热点，热点直接走本地缓存，非热点走 DB 并尝试回填。这样缓存资源集中给真正有价值的 key，避免全量预热和盲目缓存。配置里用了 HotKey 的 caffeineSize，所以本地层本质是 Caffeine 容器。'))
story.append(Spacer(1, 6))
story.append(sub_title('5.5 当前仓库状态说明'))
story.append(bullet('仓库里没有单独 new Caffeine Cache，而是通过 HotKey SDK 封装使用 Caffeine。'))
story.append(PageBreak())

# Section 6
story.append(section_title('6. Sentinel 限流熔断 + fallback 兜底'))
story.append(sub_title('6.1 业务目标'))
story.append(p('高峰期要避免核心列表接口被压垮。目标是做参数限流、异常率/慢调用熔断，并在触发后给出降级响应。'))
story.append(sub_title('6.2 代码证据'))
for t in [
    '注解式限流：src/main/java/com/yupi/mianshiya/controller/QuestionBankController.java:218-220',
    'block / fallback 处理：QuestionBankController.java:239-256',
    '编程式限流：src/main/java/com/yupi/mianshiya/controller/QuestionController.java:250-283',
    '规则加载：src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:42-73',
    '规则持久化：src/main/java/com/yupi/mianshiya/sentinel/SentinelRulesManager.java:88-106',
    '资源名常量：src/main/java/com/yupi/mianshiya/sentinel/SentinelConstant.java:14-19',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('6.3 关键实现链路'))
for t in [
    'QuestionBank 列表接口使用 @SentinelResource 绑定 blockHandler 和 fallback。',
    'Question 列表接口使用 SphU.entry，按 remoteAddr 作为热点参数做精细限流。',
    'SentinelRulesManager 在启动时加载参数限流规则 + 两类熔断规则（慢调用比例、异常比例）。',
    '规则通过 FileRefreshableDataSource / FileWritableDataSource 持久化到本地 sentinel 目录。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''// SentinelRulesManager.initFlowRules
ParamFlowRule rule = new ParamFlowRule(SentinelConstant.listQuestionVOByPage)
    .setParamIdx(0)
    .setCount(60)
    .setDurationInSec(60);
ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
'''))
story.append(Spacer(1, 8))
story.append(sub_title('6.4 面试可复述话术（60 秒版）'))
story.append(p('我在核心分页接口做了 Sentinel 双路径接入：注解式便于统一治理，编程式便于按 IP 透传参数做热点限流。规则层面除了 QPS，还加了慢调用比例和异常比例熔断。熔断触发后走 fallback 降级，避免故障扩散，保障主链路可用。'))
story.append(Spacer(1, 6))
story.append(sub_title('6.5 当前仓库状态说明'))
story.append(bullet('当前 fallback 方法返回的是 null（演示版兜底），可按生产需求替换为本地缓存或静态兜底数据。'))
story.append(PageBreak())

# Section 7
story.append(section_title('7. Redis 频率统计 + Lua 原子计数 + 自动封禁'))
story.append(sub_title('7.1 业务目标'))
story.append(p('题目详情接口容易被高频爬取。目标是做分钟级访问频率统计，达到阈值自动告警/封禁，且计数和过期要原子。'))
story.append(sub_title('7.2 代码证据'))
for t in [
    '计数器管理：src/main/java/com/yupi/mianshiya/manager/CounterManager.java:71-113',
    'Lua 脚本：CounterManager.java:95-102',
    '题目接口风控入口：src/main/java/com/yupi/mianshiya/controller/QuestionController.java:157-205',
    '封禁动作：QuestionController.java:193-199（踢下线 + 角色设为 ban）',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(sub_title('7.3 关键实现链路'))
for t in [
    '按用户维度拼接 key（user:access:{userId}）。',
    '按时间粒度生成 timeFactor，形成滑动窗口近似统计键。',
    'Lua 原子执行 exists/incr/set/expire，避免并发竞争导致 TTL 丢失。',
    'WARN_COUNT=10 触发告警；BAN_COUNT=20 触发踢下线 + 账号封禁。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 8))
story.append(code_block('''-- CounterManager 中的 Lua 逻辑
if redis.call('exists', KEYS[1]) == 1 then
  return redis.call('incr', KEYS[1]);
else
  redis.call('set', KEYS[1], 1);
  redis.call('expire', KEYS[1], ARGV[1]);
  return 1;
end
'''))
story.append(Spacer(1, 8))
story.append(sub_title('7.4 面试可复述话术（60 秒版）'))
story.append(p('我在题目详情读取链路加了用户访问频率控制。计数器用 Redis + Lua，保证“自增 + 首次过期设置”原子执行，不会因为并发导致窗口失真。阈值分两级：先告警，再封禁并踢下线。这个方案成本低、可解释性强，特别适合内容防爬。'))
story.append(PageBreak())

# Section 8
story.append(section_title('8. 端到端调用链速记（面试白板版）'))
story.append(sub_title('8.1 搜索链路'))
story.append(code_block('''Controller.searchQuestionVOByPage
  -> Service.searchFromEs (BoolQuery + match + sort)
  -> ElasticsearchRestTemplate.search
  -> DTO -> Entity
  -> VO 封装返回
'''))
story.append(sub_title('8.2 同步链路'))
story.append(code_block('''@Scheduled IncSync
  -> MySQL 按 updateTime 拉增量
  -> objToDto
  -> QuestionEsDao.saveAll(批量)
  -> ES 文档按 id 幂等覆盖
'''))
story.append(sub_title('8.3 风控链路'))
story.append(code_block('''QuestionController.getQuestionVOById
  -> crawlerDetect(userId)
  -> CounterManager.incrAndGetCounter
  -> Redis Lua 原子计数
  -> 告警 / 封禁 / 踢下线
'''))
story.append(PageBreak())

# Section 9
story.append(section_title('9. 高频追问与标准回答'))
qa_list = [
    ('为什么不直接用 MySQL 全文索引？', '中文分词灵活性、相关性排序能力和多条件组合检索上，ES 更适合复杂搜索场景；MySQL 保留为主数据源和降级路径。'),
    ('ES 数据一致性怎么保证？', '主写 MySQL，定时任务增量同步到 ES，文档主键是业务 id，保证幂等；全量任务用于回补。'),
    ('为什么要“先 ES 后回表”？', '动静分离。ES 承担召回，回表保证动态字段实时性，避免每次写都同步 ES 的高成本。'),
    ('BitMap 的空间优势怎么量化？', '365 天只需约 365 bit（约 46 字节）级别；比按天存记录大幅节省存储和索引开销。'),
    ('HotKey 和普通缓存区别？', 'HotKey是“自动探测热点 + 定向缓存”，避免无差别缓存造成资源浪费。'),
    ('Sentinel 的价值是什么？', '将“限流、熔断、降级”策略从业务代码中抽离并统一治理，故障时可快速止血。'),
    ('为什么 Lua 必须要用？', 'INCR + EXPIRE 分两条命令有竞态，Lua 保证原子性，窗口统计才可靠。'),
    ('反爬为什么按用户维度而不是 IP？', '登录用户维度更准确，能减少 NAT 场景误伤。也可叠加 IP、设备号形成多维策略。'),
    ('如果 Redis 挂了怎么办？', '降级为更宽松策略（仅日志告警），避免误封；同时把风控开关做成可动态配置。'),
    ('你做过哪些可观测性补充？', '建议补接口耗时、限流命中率、熔断次数、封禁数量等指标，便于策略调优。'),
]
for i, (q, a) in enumerate(qa_list, start=1):
    story.append(bullet(f'Q{i}: {q}'))
    story.append(Paragraph('A: ' + escape(a), styles['NoteCN']))
    story.append(Spacer(1, 4))

story.append(PageBreak())

# Section 10
story.append(section_title('10. 当前代码与简历描述对齐建议'))
story.append(p('为了确保面试“讲的就是代码里有的”，建议你按下面口径处理。'))
for t in [
    'ES 搜索：代码里已有完整 searchFromEs，但控制器默认降级走 MySQL。面试可说“线上可通过开关切 ES，当前仓库保留降级策略”。',
    '动静分离：当前仓库在 Post 模块体现最完整（ES 召回后回表拿动态字段）。你可以说“题目模块同构采用该模式”。',
    '定时同步：任务存在且可运行，但类命名有历史痕迹（Post/Question 混用）。你可主动说明“教学阶段重构遗留，不影响核心机制”。',
    'Sentinel fallback：当前返回 null。面试时可以承认“演示版”，并补充“生产应返回本地缓存或静态兜底数据”。',
    'Caffeine：通过 HotKey SDK 内部使用，不是手写 Caffeine Cache。回答时用“HotKey + 本地 Caffeine 容器”更准确。',
]:
    story.append(bullet(t))
story.append(Spacer(1, 10))
story.append(p('你在面试里最稳妥的策略：先讲“已落地代码”，再讲“我会怎样把它推到生产级”。这样既真实，又体现工程能力。'))

story.append(Spacer(1, 16))
story.append(Paragraph('—— 报告结束 ——', styles['SubTitleCN']))


doc.build(story, onFirstPage=add_page_number, onLaterPages=add_page_number)
print(output_path)
