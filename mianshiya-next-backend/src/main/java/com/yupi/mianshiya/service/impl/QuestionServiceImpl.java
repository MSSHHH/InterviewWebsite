package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.constant.CommonConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.model.dto.ai.AiToolCall;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import com.yupi.mianshiya.model.dto.ai.AiToolDefinition;
import com.yupi.mianshiya.model.dto.question.GeneratedQuestionBatch;
import com.yupi.mianshiya.model.dto.question.GeneratedQuestionItem;
import com.yupi.mianshiya.mapper.QuestionMapper;
import com.yupi.mianshiya.model.dto.question.QuestionEsDTO;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.model.enums.QuestionDifficultyEnum;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.model.vo.UserVO;
import com.yupi.mianshiya.service.QuestionBankQuestionService;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserService;
import com.yupi.mianshiya.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private static final String AI_GENERATE_QUESTION_TOOL_NAME = "submit_generated_questions";

    private static final String AI_REVIEW_TAG = "[\"待审核\"]";

    private static final int AI_GENERATE_TOOL_MAX_ATTEMPTS = 2;

    private static final String DEFAULT_QUESTION_DIFFICULTY = QuestionDifficultyEnum.MEDIUM.getValue();

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private AiManager aiManager;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        // todo 从对象中取值
        String title = question.getTitle();
        String content = question.getContent();
        String difficulty = question.getDifficulty();
        // 创建数据时，参数不能为空
        if (add) {
            // todo 补充校验规则
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(StringUtils.isBlank(difficulty), ErrorCode.PARAMS_ERROR, "题目难度不能为空");
        }
        // 修改数据时，有参数则校验
        // todo 补充校验规则
        if (StringUtils.isNotBlank(title)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content)) {
            ThrowUtils.throwIf(content.length() > 10240, ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(difficulty)) {
            ThrowUtils.throwIf(!QuestionDifficultyEnum.isValid(difficulty), ErrorCode.PARAMS_ERROR, "题目难度非法");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        // 从请求对象中提取查询参数
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        String answer = questionQueryRequest.getAnswer();
        String difficulty = questionQueryRequest.getDifficulty();
        // MySQL 查询场景：使用 like 实现模糊匹配
        // 这里保留这段逻辑，作为 ES 不可用时的兜底能力
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        // 单字段模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        // tags 在库里是 JSON 字符串，这里通过 like 做包含匹配
        // 例如 tag = Java 时匹配 "Java"
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 精确过滤条件
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        // 排序规则（先校验字段合法性，防止恶意注入）
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 对象转封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);
        // endregion
        return questionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.objToVo(question);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        questionVOList.forEach(questionVO -> {
            Long userId = questionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 分页获取题目列表
     *
     * @param questionQueryRequest
     * @return
     */
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 题目表的查询条件
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        // 根据题库查询题目列表接口
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if (questionBankId != null) {
            // 查询题库内的题目 id
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion::getQuestionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
            if (CollUtil.isNotEmpty(questionList)) {
                // 取出题目 id 集合
                Set<Long> questionIdSet = questionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
                // 复用原有题目表的查询条件
                queryWrapper.in("id", questionIdSet);
            } else {
                // 题库为空，则返回空列表
                return new Page<>(current, size, 0);
            }
        }
        // 查询数据库
        Page<Question> questionPage = this.page(new Page<>(current, size), queryWrapper);
        return questionPage;
    }

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        // 1) 获取请求参数
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        List<String> tags = questionQueryRequest.getTags();
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        String difficulty = questionQueryRequest.getDifficulty();
        // ES 的分页从 0 开始，因此要对当前页 -1
        int current = questionQueryRequest.getCurrent() - 1;
        int pageSize = questionQueryRequest.getPageSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // 2) 构造 ES Bool 查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 基础过滤：逻辑删除数据不参与检索
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        if (StringUtils.isNotBlank(difficulty)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("difficulty", difficulty));
        }
        // 动静分离：
        // 题库与题目的关系表是高频变更关系，不直接塞进 ES 文档，
        // 检索时先查 DB 关系表拿到题目 id，再作为 terms 过滤条件约束 ES。
        if (questionBankId != null) {
            LambdaQueryWrapper<QuestionBankQuestion> relationQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion::getQuestionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);

            List<Long> questionIdList = questionBankQuestionService.listObjs(relationQueryWrapper, obj -> (Long) obj);
            if (CollUtil.isEmpty(questionIdList)) {
                return new Page<>(questionQueryRequest.getCurrent(), questionQueryRequest.getPageSize(), 0);
            }
            boolQueryBuilder.filter(QueryBuilders.termsQuery("id", questionIdList));
        }
        // 标签过滤：每个 tag 都使用 filter，相当于“必须全部命中”
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }
        // 全文检索：title / content / answer 任一字段匹配即召回
        if (StringUtils.isNotBlank(searchText)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 3) 排序策略
        // 默认按相关性分数排序；若前端传了排序字段，则按字段排序
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }
        // 4) 分页 + 执行查询
        PageRequest pageRequest = PageRequest.of(current, pageSize);
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder)
                .build();
        SearchHits<QuestionEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery , QuestionEsDTO.class);
        Page<Question> page = new Page<>(questionQueryRequest.getCurrent(), questionQueryRequest.getPageSize(), searchHits.getTotalHits());
        if (!searchHits.hasSearchHits()) {
            return page;
        }

        // 5) 动静分离核心：
        // ES 只负责召回 id（静态/检索友好字段），
        // 返回前再去 MySQL 按 id 批量取最新实体（动态/高频变更字段以 DB 为准）
        List<Long> questionIdList = searchHits.getSearchHits().stream()
                .map(searchHit -> searchHit.getContent().getId())
                .collect(Collectors.toList());
        List<Question> questionList = this.listByIds(questionIdList);
        Map<Long, Question> questionIdQuestionMap = questionList.stream()
                .collect(Collectors.toMap(Question::getId, question -> question, (left, right) -> left));
        // 这里按 ES 命中顺序组装最终结果，避免 listByIds 打乱排序
        List<Question> latestQuestionList = new ArrayList<>(questionIdList.size());
        for (Long questionId : questionIdList) {
            Question latestQuestion = questionIdQuestionMap.get(questionId);
            if (latestQuestion != null) {
                latestQuestionList.add(latestQuestion);
                continue;
            }
            // 清理 ES 中存在、DB 中不存在的脏数据
            String deleteResult = elasticsearchRestTemplate.delete(String.valueOf(questionId), QuestionEsDTO.class);
            log.info("delete dirty es question {}, result {}", questionId, deleteResult);
        }
        page.setRecords(latestQuestionList);
        return page;
    }

    /**
     * 批量删除题目
     *
     * @param questionIdList
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteQuestions(List<Long> questionIdList) {
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "要删除的题目列表不能为空");
        for (Long questionId : questionIdList) {
            boolean result = this.removeById(questionId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目失败");
            // 移除题目题库关系
            // 构造查询
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId);
            result = questionBankQuestionService.remove(lambdaQueryWrapper);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目题库关联失败");
        }
    }

    /**
     * AI 生成题目
     *
     * @param questionType 题目类型，比如 Java
     * @param number       题目数量，比如 10
     * @param user         创建人
     * @return ture / false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean aiGenerateQuestions(String questionType, int number, User user) {
        if (ObjectUtil.hasEmpty(questionType, number, user)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        GeneratedQuestionBatch generatedQuestionBatch = generateStructuredQuestions(questionType, number);
        List<Question> questionList = generatedQuestionBatch.getQuestions().stream().map(generatedQuestionItem -> {
            String title = StrUtil.trim(generatedQuestionItem.getTitle());
            Question question = new Question();
            question.setTitle(title);
            question.setUserId(user.getId());
            question.setTags(AI_REVIEW_TAG);
            question.setDifficulty(DEFAULT_QUESTION_DIFFICULTY);
            question.setAnswer(aiGenerateQuestionAnswer(title));
            validQuestion(question, true);
            return question;
        }).collect(Collectors.toList());
        boolean result = this.saveBatch(questionList);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存题目失败");
        }
        log.info("ai generate questions success, questionType={}, requestedCount={}, savedCount={}, userId={}",
                questionType, number, questionList.size(), user.getId());
        return true;
    }

    private GeneratedQuestionBatch generateStructuredQuestions(String questionType, int number) {
        AiToolDefinition generateQuestionTool = buildGenerateQuestionTool();
        String systemPrompt = "你是一位专业的程序员面试官，负责生成结构化面试题。\n" +
                "你必须调用 submit_generated_questions 工具输出结果，不能返回普通文本。\n" +
                "每道题只保留题目标题，不要附带答案、序号、解释、Markdown 标记或额外说明。\n" +
                "题目要围绕给定技术方向，语义专业、表述自然、适合作为程序员面试题。";
        String validationError = null;
        for (int attempt = 1; attempt <= AI_GENERATE_TOOL_MAX_ATTEMPTS; attempt++) {
            String userPrompt = buildGenerateQuestionUserPrompt(questionType, number, validationError);
            AiToolChatResult aiToolChatResult = aiManager.doToolChat(systemPrompt,
                    userPrompt,
                    Collections.singletonList(generateQuestionTool),
                    AI_GENERATE_QUESTION_TOOL_NAME);
            GeneratedQuestionBatch generatedQuestionBatch = parseGeneratedQuestionBatch(aiToolChatResult);
            validationError = validateGeneratedQuestionBatch(generatedQuestionBatch, number);
            boolean toolHit = CollUtil.isNotEmpty(aiToolChatResult.getToolCalls());
            log.info("ai generate questions tool call, requestId={}, model={}, toolName={}, questionType={}, requestedCount={}, toolHit={}, attempt={}",
                    aiToolChatResult.getRequestId(),
                    aiToolChatResult.getModel(),
                    AI_GENERATE_QUESTION_TOOL_NAME,
                    questionType,
                    number,
                    toolHit,
                    attempt);
            if (validationError == null) {
                normalizeGeneratedQuestionBatch(generatedQuestionBatch);
                return generatedQuestionBatch;
            }
            log.warn("ai generate questions validation failed, requestId={}, toolName={}, questionType={}, requestedCount={}, attempt={}, reason={}",
                    aiToolChatResult.getRequestId(),
                    AI_GENERATE_QUESTION_TOOL_NAME,
                    questionType,
                    number,
                    attempt,
                    validationError);
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 生成题目失败，结构化结果校验未通过");
    }

    private String buildGenerateQuestionUserPrompt(String questionType, int number, String validationError) {
        String prompt = String.format("请生成 %d 道 %s 面试题，并且必须通过 submit_generated_questions 工具一次性返回结构化结果。",
                number, questionType);
        if (StrUtil.isBlank(validationError)) {
            return prompt;
        }
        return prompt + String.format(" 上一次返回结果不合法，原因是：%s。请严格修正后重新调用工具。", validationError);
    }

    private AiToolDefinition buildGenerateQuestionTool() {
        ObjectNode titleSchema = objectMapper.createObjectNode();
        titleSchema.put("type", "string");
        titleSchema.put("description", "面试题标题，只能是单行文本");

        ObjectNode itemSchema = objectMapper.createObjectNode();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        ObjectNode itemProperties = objectMapper.createObjectNode();
        itemProperties.set("title", titleSchema);
        itemSchema.set("properties", itemProperties);
        ArrayNode itemRequired = objectMapper.createArrayNode();
        itemRequired.add("title");
        itemSchema.set("required", itemRequired);

        ObjectNode questionsSchema = objectMapper.createObjectNode();
        questionsSchema.put("type", "array");
        questionsSchema.put("description", "结构化面试题列表");
        questionsSchema.set("items", itemSchema);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        parameters.put("additionalProperties", false);
        ObjectNode parameterProperties = objectMapper.createObjectNode();
        parameterProperties.set("questions", questionsSchema);
        parameters.set("properties", parameterProperties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("questions");
        parameters.set("required", required);

        return AiToolDefinition.builder()
                .name(AI_GENERATE_QUESTION_TOOL_NAME)
                .description("提交结构化生成的面试题列表")
                .parameters(parameters)
                .build();
    }

    private GeneratedQuestionBatch parseGeneratedQuestionBatch(AiToolChatResult aiToolChatResult) {
        List<AiToolCall> toolCalls = aiToolChatResult.getToolCalls();
        if (CollUtil.isEmpty(toolCalls)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未返回工具调用结果");
        }
        AiToolCall matchedToolCall = toolCalls.stream()
                .filter(Objects::nonNull)
                .filter(toolCall -> AI_GENERATE_QUESTION_TOOL_NAME.equals(toolCall.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未返回预期工具调用结果"));
        String arguments = matchedToolCall.getArguments();
        if (StrUtil.isBlank(arguments)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用参数为空");
        }
        try {
            return objectMapper.readValue(arguments, GeneratedQuestionBatch.class);
        } catch (Exception e) {
            log.warn("parse generated question batch failed, requestId={}, toolName={}, message={}",
                    aiToolChatResult.getRequestId(), AI_GENERATE_QUESTION_TOOL_NAME, e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用参数解析失败");
        }
    }

    private String validateGeneratedQuestionBatch(GeneratedQuestionBatch generatedQuestionBatch, int expectedNumber) {
        if (generatedQuestionBatch == null || CollUtil.isEmpty(generatedQuestionBatch.getQuestions())) {
            return "questions 不能为空";
        }
        List<GeneratedQuestionItem> generatedQuestionItems = generatedQuestionBatch.getQuestions();
        if (generatedQuestionItems.size() != expectedNumber) {
            return String.format("题目数量不正确，期望 %s，实际 %s", expectedNumber, generatedQuestionItems.size());
        }
        Set<String> uniqueTitles = new LinkedHashSet<>();
        for (GeneratedQuestionItem generatedQuestionItem : generatedQuestionItems) {
            if (generatedQuestionItem == null) {
                return "questions 中存在空对象";
            }
            String title = StrUtil.trim(generatedQuestionItem.getTitle());
            if (StrUtil.isBlank(title)) {
                return "题目标题不能为空";
            }
            uniqueTitles.add(title);
        }
        if (uniqueTitles.size() != expectedNumber) {
            return "题目标题存在重复";
        }
        return null;
    }

    private void normalizeGeneratedQuestionBatch(GeneratedQuestionBatch generatedQuestionBatch) {
        List<GeneratedQuestionItem> normalizedQuestionItems = generatedQuestionBatch.getQuestions().stream().map(generatedQuestionItem -> {
            GeneratedQuestionItem normalizedQuestionItem = new GeneratedQuestionItem();
            normalizedQuestionItem.setTitle(StrUtil.trim(generatedQuestionItem.getTitle()));
            return normalizedQuestionItem;
        }).collect(Collectors.toList());
        generatedQuestionBatch.setQuestions(normalizedQuestionItems);
    }

    /**
     * AI 生成题解
     *
     * @param questionTitle
     * @return
     */
    private String aiGenerateQuestionAnswer(String questionTitle) {
        // 1. 定义系统 Prompt
        String systemPrompt = "你是一位专业的程序员面试官，我会给你一道面试题，请帮我生成详细的题解。要求如下：\n" +
                "\n" +
                "1. 题解的语句要自然流畅\n" +
                "2. 题解可以先给出总结性的回答，再详细解释\n" +
                "3. 要使用 Markdown 语法输出\n" +
                "\n" +
                "除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出题解。\n" +
                "\n" +
                "接下来我会给你要生成的面试题";
        // 2. 拼接用户 Prompt
        String userPrompt = String.format("面试题：%s", questionTitle);
        // 3. 调用 AI 生成题解
        return aiManager.doChat(systemPrompt, userPrompt);
    }

}
