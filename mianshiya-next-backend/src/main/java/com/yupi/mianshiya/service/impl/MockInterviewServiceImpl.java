package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.constant.CommonConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.mapper.MockInterviewMapper;
import com.yupi.mianshiya.model.dto.ai.AiChatMessage;
import com.yupi.mianshiya.model.dto.ai.AiChatMessageRole;
import com.yupi.mianshiya.model.dto.ai.AiToolCall;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import com.yupi.mianshiya.model.dto.ai.AiToolDefinition;
import com.yupi.mianshiya.model.dto.mockinterview.InterviewQuestionSearchResult;
import com.yupi.mianshiya.model.dto.mockinterview.InterviewToolCallContext;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewAddRequest;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewChatMessage;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewEventRequest;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewEventResponse;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewQueryRequest;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewReport;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.MockInterview;
import com.yupi.mianshiya.model.entity.QuestionBank;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.enums.MockInterviewEventEnum;
import com.yupi.mianshiya.model.enums.MockInterviewStatusEnum;
import com.yupi.mianshiya.model.enums.QuestionDifficultyEnum;
import com.yupi.mianshiya.service.MockInterviewService;
import com.yupi.mianshiya.service.QuestionBankService;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模拟面试服务实现
 */
@Service
@Slf4j
public class MockInterviewServiceImpl extends ServiceImpl<MockInterviewMapper, MockInterview>
        implements MockInterviewService {

    private static final int MAX_TOOL_ROUNDS = 6;

    private static final int MAX_INTERVIEW_QUESTION_COUNT = 20;

    private static final int DEFAULT_SEARCH_LIMIT = 5;

    private static final String TOOL_SEARCH_QUESTIONS = "search_questions_by_topic";

    private static final String TOOL_GET_QUESTION_DETAIL = "get_question_detail";

    private static final String TOOL_GET_STANDARD_ANSWER = "get_standard_answer";

    private static final String TOOL_FINISH_INTERVIEW = "finish_interview_and_generate_report";

    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AiManager aiManager;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 创建模拟面试
     *
     * @param mockInterviewAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User loginUser) {
        if (mockInterviewAddRequest == null || loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String workExperience = StrUtil.trim(mockInterviewAddRequest.getWorkExperience());
        String jobPosition = StrUtil.trim(mockInterviewAddRequest.getJobPosition());
        String difficulty = StrUtil.trim(mockInterviewAddRequest.getDifficulty());
        Long questionBankId = mockInterviewAddRequest.getQuestionBankId();
        ThrowUtils.throwIf(StrUtil.hasBlank(workExperience, jobPosition, difficulty), ErrorCode.PARAMS_ERROR, "参数错误");
        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "请选择题库方向");
        ThrowUtils.throwIf(!QuestionDifficultyEnum.isValid(difficulty), ErrorCode.PARAMS_ERROR, "面试难度非法");
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.PARAMS_ERROR, "题库不存在");
        MockInterview mockInterview = new MockInterview();
        mockInterview.setWorkExperience(workExperience);
        mockInterview.setJobPosition(jobPosition);
        mockInterview.setDifficulty(difficulty);
        mockInterview.setTopic(questionBank.getTitle());
        mockInterview.setQuestionBankId(questionBankId);
        mockInterview.setUserId(loginUser.getId());
        mockInterview.setStatus(MockInterviewStatusEnum.TO_START.getValue());
        boolean result = this.save(mockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建失败");
        return mockInterview.getId();
    }

    /**
     * 获取查询条件
     *
     * @param mockInterviewQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest) {
        QueryWrapper<MockInterview> queryWrapper = new QueryWrapper<>();
        if (mockInterviewQueryRequest == null) {
            return queryWrapper;
        }
        Long id = mockInterviewQueryRequest.getId();
        String workExperience = mockInterviewQueryRequest.getWorkExperience();
        String jobPosition = mockInterviewQueryRequest.getJobPosition();
        String difficulty = mockInterviewQueryRequest.getDifficulty();
        String topic = mockInterviewQueryRequest.getTopic();
        Long questionBankId = mockInterviewQueryRequest.getQuestionBankId();
        Integer status = mockInterviewQueryRequest.getStatus();
        Long userId = mockInterviewQueryRequest.getUserId();
        String sortField = mockInterviewQueryRequest.getSortField();
        String sortOrder = mockInterviewQueryRequest.getSortOrder();
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.like(StringUtils.isNotBlank(workExperience), "workExperience", workExperience);
        queryWrapper.like(StringUtils.isNotBlank(jobPosition), "jobPosition", jobPosition);
        queryWrapper.like(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        queryWrapper.like(StringUtils.isNotBlank(topic), "topic", topic);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    /**
     * 处理模拟面试事件
     *
     * @param mockInterviewEventRequest
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewEventResponse handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser) {
        Long id = mockInterviewEventRequest.getId();
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "参数错误");
        MockInterview mockInterview = this.getById(id);
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.PARAMS_ERROR, "模拟面试未创建");
        if (!mockInterview.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        String event = mockInterviewEventRequest.getEvent();
        MockInterviewEventEnum eventEnum = MockInterviewEventEnum.getEnumByValue(event);
        ThrowUtils.throwIf(eventEnum == null, ErrorCode.PARAMS_ERROR, "事件类型错误");
        switch (eventEnum) {
            case START:
                ThrowUtils.throwIf(MockInterviewStatusEnum.TO_START.getValue() != mockInterview.getStatus(),
                        ErrorCode.OPERATION_ERROR, "模拟面试已开始");
                return handleChatStartEvent(mockInterview);
            case CHAT:
                ThrowUtils.throwIf(MockInterviewStatusEnum.IN_PROGRESS.getValue() != mockInterview.getStatus(),
                        ErrorCode.OPERATION_ERROR, "模拟面试未开始或已结束");
                return handleChatMessageEvent(mockInterviewEventRequest, mockInterview);
            case END:
                ThrowUtils.throwIf(MockInterviewStatusEnum.ENDED.getValue() == mockInterview.getStatus(),
                        ErrorCode.OPERATION_ERROR, "模拟面试已结束");
                return handleChatEndEvent(mockInterview);
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
    }

    private MockInterviewEventResponse handleChatStartEvent(MockInterview mockInterview) {
        InterviewToolCallContext context = buildToolCallContext(mockInterview, Collections.emptyList());
        List<AiChatMessage> messages = buildConversationMessages(mockInterview, Collections.emptyList(), context);
        messages.add(buildMessage(AiChatMessageRole.USER, "请正式开始这场模拟面试。"));
        AiToolChatResult aiToolChatResult = aiManager.doToolLoopChat(
                messages,
                buildInterviewTools(),
                toolCall -> executeInterviewToolCall(toolCall, mockInterview, context),
                MAX_TOOL_ROUNDS,
                aiManager.getDefaultModel()
        );
        String finalAnswer = resolveFinalAnswer(aiToolChatResult, context);
        List<MockInterviewChatMessage> newHistory = new ArrayList<>();
        newHistory.add(buildAssistantHistoryMessage(finalAnswer, context));
        saveInterviewProgress(mockInterview.getId(), MockInterviewStatusEnum.IN_PROGRESS.getValue(), newHistory, null);
        return buildEventResponse(mockInterview.getId(), finalAnswer);
    }

    private MockInterviewEventResponse handleChatMessageEvent(MockInterviewEventRequest mockInterviewEventRequest, MockInterview mockInterview) {
        String userMessage = StrUtil.trim(mockInterviewEventRequest.getMessage());
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "消息不能为空");
        List<MockInterviewChatMessage> historyMessageList = parseHistoryMessages(mockInterview.getMessages());
        InterviewToolCallContext context = buildToolCallContext(mockInterview, historyMessageList);
        List<AiChatMessage> messages = buildConversationMessages(mockInterview, historyMessageList, context);
        messages.add(buildMessage(AiChatMessageRole.USER, userMessage));
        AiToolChatResult aiToolChatResult = aiManager.doToolLoopChat(
                messages,
                buildInterviewTools(),
                toolCall -> executeInterviewToolCall(toolCall, mockInterview, context),
                MAX_TOOL_ROUNDS,
                aiManager.getDefaultModel()
        );
        String finalAnswer = resolveFinalAnswer(aiToolChatResult, context);
        historyMessageList.add(buildUserHistoryMessage(userMessage));
        historyMessageList.add(buildAssistantHistoryMessage(finalAnswer, context));
        saveInterviewProgress(mockInterview.getId(),
                context.isEnded() ? MockInterviewStatusEnum.ENDED.getValue() : MockInterviewStatusEnum.IN_PROGRESS.getValue(),
                historyMessageList,
                context.getReport());
        return buildEventResponse(mockInterview.getId(), finalAnswer);
    }

    private MockInterviewEventResponse handleChatEndEvent(MockInterview mockInterview) {
        List<MockInterviewChatMessage> historyMessageList = parseHistoryMessages(mockInterview.getMessages());
        InterviewToolCallContext context = buildToolCallContext(mockInterview, historyMessageList);
        List<AiChatMessage> messages = buildConversationMessages(mockInterview, historyMessageList, context);
        messages.add(buildMessage(AiChatMessageRole.USER, "候选人要求立即结束面试。请生成完整的面试报告并给出最终总结。"));
        AiToolChatResult aiToolChatResult = aiManager.doToolLoopChat(
                messages,
                buildInterviewTools(),
                toolCall -> executeInterviewToolCall(toolCall, mockInterview, context),
                MAX_TOOL_ROUNDS,
                aiManager.getDefaultModel()
        );
        ThrowUtils.throwIf(context.getReport() == null, ErrorCode.OPERATION_ERROR, "结束面试失败，未生成报告");
        String finalAnswer = resolveFinalAnswer(aiToolChatResult, context);
        historyMessageList.add(buildAssistantHistoryMessage(finalAnswer, context));
        saveInterviewProgress(mockInterview.getId(), MockInterviewStatusEnum.ENDED.getValue(), historyMessageList, context.getReport());
        return buildEventResponse(mockInterview.getId(), finalAnswer);
    }

    private MockInterviewEventResponse buildEventResponse(Long mockInterviewId, String aiResponse) {
        MockInterview latestInterview = this.getById(mockInterviewId);
        ThrowUtils.throwIf(latestInterview == null, ErrorCode.NOT_FOUND_ERROR, "模拟面试不存在");
        MockInterviewEventResponse eventResponse = new MockInterviewEventResponse();
        eventResponse.setAiResponse(aiResponse);
        eventResponse.setMockInterview(latestInterview);
        return eventResponse;
    }

    private InterviewToolCallContext buildToolCallContext(MockInterview mockInterview, List<MockInterviewChatMessage> historyMessageList) {
        InterviewToolCallContext context = new InterviewToolCallContext();
        context.setMockInterviewId(mockInterview.getId());
        context.setTargetDifficulty(mockInterview.getDifficulty());
        Set<Long> askedQuestionIds = historyMessageList.stream()
                .map(MockInterviewChatMessage::getQuestionId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        context.getAskedQuestionIds().addAll(askedQuestionIds);
        for (int i = historyMessageList.size() - 1; i >= 0; i--) {
            MockInterviewChatMessage historyMessage = historyMessageList.get(i);
            if (historyMessage.getQuestionId() != null) {
                context.setCurrentQuestionId(historyMessage.getQuestionId());
                context.setCurrentQuestionTitle(historyMessage.getQuestionTitle());
                break;
            }
        }
        if (StringUtils.isNotBlank(mockInterview.getReport())) {
            try {
                context.setReport(objectMapper.readValue(mockInterview.getReport(), MockInterviewReport.class));
            } catch (JsonProcessingException e) {
                log.warn("parse mock interview report failed, interviewId={}", mockInterview.getId(), e);
            }
        }
        return context;
    }

    private List<AiChatMessage> buildConversationMessages(MockInterview mockInterview,
                                                          List<MockInterviewChatMessage> historyMessageList,
                                                          InterviewToolCallContext context) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(AiChatMessageRole.SYSTEM, buildSystemPrompt(mockInterview)));
        messages.add(buildMessage(AiChatMessageRole.SYSTEM, buildRuntimeContextPrompt(mockInterview, context)));
        for (MockInterviewChatMessage historyMessage : historyMessageList) {
            if (StringUtils.isBlank(historyMessage.getRole()) || StringUtils.isBlank(historyMessage.getMessage())) {
                continue;
            }
            AiChatMessageRole role = AiChatMessageRole.fromValue(historyMessage.getRole());
            if (role == null) {
                continue;
            }
            if (role != AiChatMessageRole.USER && role != AiChatMessageRole.ASSISTANT) {
                continue;
            }
            messages.add(buildMessage(role, historyMessage.getMessage()));
        }
        return messages;
    }

    private String buildSystemPrompt(MockInterview mockInterview) {
        return String.format("你是一位严格但专业的程序员面试官，正在对候选人进行 %s 岗位模拟面试。候选人工作年限为 %s，目标面试难度上限为 %s，面试方向为 %s。\n" +
                        "你必须遵守以下规则：\n" +
                        "1. 你不能凭空杜撰题目，必须优先使用工具从题库中选题。\n" +
                        "2. 开始面试时，必须先调用 %s 和 %s，再正式向候选人提问。\n" +
                        "3. 候选人回答后，若要点评当前题，优先调用 %s 获取标准答案，再给出评价。\n" +
                        "4. 每次只问一道题，不要一次抛出多道题。\n" +
                        "5. 题目难度可以逐步提升，但不能超过本次面试的目标难度。\n" +
                        "6. 不允许重复提问已经问过的题目。\n" +
                        "7. 当候选人明确要求结束面试，或你判断面试可以结束时，必须调用 %s 生成结构化报告。\n" +
                        "8. 调用 %s 后，再输出给用户的最终总结，语言自然，和报告保持一致。\n",
                mockInterview.getJobPosition(),
                mockInterview.getWorkExperience(),
                mockInterview.getDifficulty(),
                mockInterview.getTopic(),
                TOOL_SEARCH_QUESTIONS,
                TOOL_GET_QUESTION_DETAIL,
                TOOL_GET_STANDARD_ANSWER,
                TOOL_FINISH_INTERVIEW,
                TOOL_FINISH_INTERVIEW);
    }

    private String buildRuntimeContextPrompt(MockInterview mockInterview, InterviewToolCallContext context) {
        return String.format("当前会话上下文：\n" +
                        "- 面试方向：%s\n" +
                        "- 已提问题目 ID：%s\n" +
                        "- 当前最近一道题 ID：%s\n" +
                        "- 当前最近一道题标题：%s\n" +
                        "- 已结束：%s\n" +
                        "如果当前最近一道题 ID 不为空，并且你要先点评答案，请直接使用这个 id 调用 %s。",
                mockInterview.getTopic(),
                context.getAskedQuestionIds(),
                context.getCurrentQuestionId(),
                context.getCurrentQuestionTitle(),
                context.isEnded(),
                TOOL_GET_STANDARD_ANSWER);
    }

    private List<AiToolDefinition> buildInterviewTools() {
        List<AiToolDefinition> tools = new ArrayList<>();
        tools.add(buildSearchQuestionsTool());
        tools.add(buildQuestionDetailTool());
        tools.add(buildStandardAnswerTool());
        tools.add(buildFinishInterviewTool());
        return tools;
    }

    private AiToolDefinition buildSearchQuestionsTool() {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("topic")
                .put("type", "string")
                .put("description", "面试方向，例如 Java 并发、Redis 高并发");
        properties.putObject("difficulty")
                .put("type", "string")
                .put("description", "目标题目难度，取值 easy / medium / hard");
        ObjectNode excludeQuestionIdsNode = properties.putObject("excludeQuestionIds");
        excludeQuestionIdsNode.put("type", "array");
        excludeQuestionIdsNode.putObject("items").put("type", "integer");
        properties.putObject("limit")
                .put("type", "integer")
                .put("description", "返回候选题数量，建议 1 - 5");
        ArrayNode required = parameters.putArray("required");
        required.add("topic");
        required.add("difficulty");
        return AiToolDefinition.builder()
                .name(TOOL_SEARCH_QUESTIONS)
                .description("按面试方向和难度搜索候选题目")
                .parameters(parameters)
                .build();
    }

    private AiToolDefinition buildQuestionDetailTool() {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("questionId")
                .put("type", "integer")
                .put("description", "题目 id");
        parameters.putArray("required").add("questionId");
        return AiToolDefinition.builder()
                .name(TOOL_GET_QUESTION_DETAIL)
                .description("获取题目详情，用于正式发问")
                .parameters(parameters)
                .build();
    }

    private AiToolDefinition buildStandardAnswerTool() {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("questionId")
                .put("type", "integer")
                .put("description", "题目 id");
        parameters.putArray("required").add("questionId");
        return AiToolDefinition.builder()
                .name(TOOL_GET_STANDARD_ANSWER)
                .description("获取题目标准答案，用于点评候选人回答")
                .parameters(parameters)
                .build();
    }

    private AiToolDefinition buildFinishInterviewTool() {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ArrayNode required = parameters.putArray("required");
        properties.putObject("questionIds")
                .put("type", "array")
                .putObject("items")
                .put("type", "integer");
        properties.putObject("overallScore")
                .put("type", "integer")
                .put("description", "0 - 100 的总分");
        properties.putObject("summary")
                .put("type", "string")
                .put("description", "整体总结");
        ObjectNode strengthsItems = properties.putObject("strengths").put("type", "array").putObject("items");
        strengthsItems.put("type", "string");
        ObjectNode weaknessesItems = properties.putObject("weaknesses").put("type", "array").putObject("items");
        weaknessesItems.put("type", "string");
        ObjectNode suggestionsItems = properties.putObject("suggestions").put("type", "array").putObject("items");
        suggestionsItems.put("type", "string");
        properties.putObject("finalMessage")
                .put("type", "string")
                .put("description", "最终返回给候选人的自然语言总结");
        required.add("overallScore");
        required.add("summary");
        required.add("strengths");
        required.add("weaknesses");
        required.add("suggestions");
        required.add("finalMessage");
        return AiToolDefinition.builder()
                .name(TOOL_FINISH_INTERVIEW)
                .description("结束面试并生成结构化报告")
                .parameters(parameters)
                .build();
    }

    private String executeInterviewToolCall(AiToolCall toolCall, MockInterview mockInterview, InterviewToolCallContext context) {
        ThrowUtils.throwIf(toolCall == null || StringUtils.isBlank(toolCall.getName()), ErrorCode.OPERATION_ERROR, "工具调用缺少函数名");
        JsonNode argumentsNode = parseArguments(toolCall.getArguments());
        switch (toolCall.getName()) {
            case TOOL_SEARCH_QUESTIONS:
                context.setLastToolName(TOOL_SEARCH_QUESTIONS);
                return handleSearchQuestions(argumentsNode, mockInterview, context);
            case TOOL_GET_QUESTION_DETAIL:
                context.setLastToolName(TOOL_GET_QUESTION_DETAIL);
                return handleGetQuestionDetail(argumentsNode, context);
            case TOOL_GET_STANDARD_ANSWER:
                context.setLastToolName(TOOL_GET_STANDARD_ANSWER);
                return handleGetStandardAnswer(argumentsNode, context);
            case TOOL_FINISH_INTERVIEW:
                context.setLastToolName(TOOL_FINISH_INTERVIEW);
                return handleFinishInterview(argumentsNode, mockInterview, context);
            default:
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未知工具调用: " + toolCall.getName());
        }
    }

    private String handleSearchQuestions(JsonNode argumentsNode, MockInterview mockInterview, InterviewToolCallContext context) {
        String topic = getNodeText(argumentsNode, "topic", mockInterview.getTopic());
        String requestedDifficulty = normalizeDifficulty(getNodeText(argumentsNode, "difficulty", context.getTargetDifficulty()));
        int limit = Math.max(1, Math.min(5, getNodeInt(argumentsNode, "limit", DEFAULT_SEARCH_LIMIT)));
        Set<Long> excludeQuestionIds = new LinkedHashSet<>(context.getAskedQuestionIds());
        JsonNode excludeQuestionIdsNode = argumentsNode.get("excludeQuestionIds");
        if (excludeQuestionIdsNode != null && excludeQuestionIdsNode.isArray()) {
            excludeQuestionIdsNode.forEach(node -> excludeQuestionIds.add(node.asLong()));
        }
        List<InterviewQuestionSearchResult> questionSearchResults =
                searchQuestionsByTopic(mockInterview.getQuestionBankId(), topic, requestedDifficulty, excludeQuestionIds, limit);
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.putPOJO("questions", questionSearchResults);
        return writeJson(resultNode);
    }

    private String handleGetQuestionDetail(JsonNode argumentsNode, InterviewToolCallContext context) {
        long questionId = getRequiredLong(argumentsNode, "questionId");
        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        context.setCurrentQuestionId(question.getId());
        context.setCurrentQuestionTitle(question.getTitle());
        context.setCurrentQuestionDifficulty(question.getDifficulty());
        context.getAskedQuestionIds().add(question.getId());
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("id", question.getId());
        resultNode.put("title", question.getTitle());
        resultNode.put("content", question.getContent());
        resultNode.put("difficulty", question.getDifficulty());
        if (StrUtil.isNotBlank(question.getTags())) {
            resultNode.set("tags", objectMapper.valueToTree(JSONUtil.toList(JSONUtil.parseArray(question.getTags()), String.class)));
        } else {
            resultNode.set("tags", objectMapper.createArrayNode());
        }
        return writeJson(resultNode);
    }

    private String handleGetStandardAnswer(JsonNode argumentsNode, InterviewToolCallContext context) {
        long questionId = getRequiredLong(argumentsNode, "questionId");
        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        context.setCurrentQuestionId(question.getId());
        context.setCurrentQuestionTitle(question.getTitle());
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("questionId", question.getId());
        resultNode.put("title", question.getTitle());
        resultNode.put("answer", question.getAnswer());
        return writeJson(resultNode);
    }

    private String handleFinishInterview(JsonNode argumentsNode, MockInterview mockInterview, InterviewToolCallContext context) {
        int overallScore = getNodeInt(argumentsNode, "overallScore", -1);
        ThrowUtils.throwIf(overallScore < 0 || overallScore > 100, ErrorCode.PARAMS_ERROR, "总分非法");
        String summary = StrUtil.trim(getNodeText(argumentsNode, "summary", null));
        String finalMessage = StrUtil.trim(getNodeText(argumentsNode, "finalMessage", null));
        List<String> strengths = getRequiredStringList(argumentsNode, "strengths");
        List<String> weaknesses = getRequiredStringList(argumentsNode, "weaknesses");
        List<String> suggestions = getRequiredStringList(argumentsNode, "suggestions");
        ThrowUtils.throwIf(StrUtil.hasBlank(summary, finalMessage), ErrorCode.PARAMS_ERROR, "报告内容不完整");
        List<Long> questionIds = extractQuestionIds(argumentsNode.get("questionIds"));
        if (CollUtil.isEmpty(questionIds)) {
            questionIds = new ArrayList<>(context.getAskedQuestionIds());
        }
        MockInterviewReport report = new MockInterviewReport();
        report.setOverallScore(overallScore);
        report.setSummary(summary);
        report.setStrengths(strengths);
        report.setWeaknesses(weaknesses);
        report.setSuggestions(suggestions);
        report.setQuestionIds(questionIds);
        report.setFinishedAt(REPORT_TIME_FORMATTER.format(LocalDateTime.now()));
        context.setReport(report);
        context.setFinalMessage(finalMessage);
        context.setEnded(true);
        MockInterview updateMockInterview = new MockInterview();
        updateMockInterview.setId(mockInterview.getId());
        updateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        updateMockInterview.setReport(writeJson(report));
        boolean result = this.updateById(updateMockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "生成面试报告失败");
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("status", "ok");
        resultNode.put("ended", true);
        resultNode.put("finalMessage", finalMessage);
        return writeJson(resultNode);
    }

    private List<InterviewQuestionSearchResult> searchQuestionsByTopic(Long questionBankId,
                                                                       String topic,
                                                                       String requestedDifficulty,
                                                                       Set<Long> excludeQuestionIds,
                                                                       int limit) {
        List<String> difficultyOrder = buildDifficultyFallbackOrder(requestedDifficulty);
        for (String difficulty : difficultyOrder) {
            List<InterviewQuestionSearchResult> resultList =
                    doSearchQuestions(questionBankId, topic, difficulty, excludeQuestionIds, limit);
            if (CollUtil.isNotEmpty(resultList)) {
                return resultList;
            }
        }
        return Collections.emptyList();
    }

    private List<InterviewQuestionSearchResult> doSearchQuestions(Long questionBankId,
                                                                  String topic,
                                                                  String difficulty,
                                                                  Set<Long> excludeQuestionIds,
                                                                  int limit) {
        if (excludeQuestionIds.size() >= MAX_INTERVIEW_QUESTION_COUNT) {
            return Collections.emptyList();
        }
        QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
        questionQueryRequest.setCurrent(1);
        questionQueryRequest.setPageSize(50);
        questionQueryRequest.setQuestionBankId(questionBankId);
        if (questionBankId == null && StrUtil.isNotBlank(topic)) {
            questionQueryRequest.setSearchText(topic);
        }
        questionQueryRequest.setDifficulty(difficulty);
        Page<Question> questionPage = null;
        try {
            questionPage = questionService.searchFromEs(questionQueryRequest);
        } catch (Exception e) {
            log.warn("search interview questions from es failed, fallback to mysql, topic={}, difficulty={}", topic, difficulty, e);
        }
        if (questionPage == null || CollUtil.isEmpty(questionPage.getRecords())) {
            questionPage = questionService.listQuestionByPage(questionQueryRequest);
        }
        return questionPage.getRecords().stream()
                .filter(question -> !excludeQuestionIds.contains(question.getId()))
                .limit(limit)
                .map(question -> {
                    InterviewQuestionSearchResult questionSearchResult = new InterviewQuestionSearchResult();
                    questionSearchResult.setId(question.getId());
                    questionSearchResult.setTitle(question.getTitle());
                    questionSearchResult.setDifficulty(question.getDifficulty());
                    if (StrUtil.isNotBlank(question.getTags())) {
                        questionSearchResult.setTags(JSONUtil.toList(JSONUtil.parseArray(question.getTags()), String.class));
                    } else {
                        questionSearchResult.setTags(Collections.emptyList());
                    }
                    return questionSearchResult;
                })
                .collect(Collectors.toList());
    }

    private List<String> buildDifficultyFallbackOrder(String difficulty) {
        String normalizedDifficulty = normalizeDifficulty(difficulty);
        switch (normalizedDifficulty) {
            case "easy":
                return Arrays.asList("easy", "medium", "hard");
            case "hard":
                return Arrays.asList("hard", "medium", "easy");
            case "medium":
            default:
                return Arrays.asList("medium", "easy", "hard");
        }
    }

    private String normalizeDifficulty(String difficulty) {
        if (!QuestionDifficultyEnum.isValid(difficulty)) {
            return QuestionDifficultyEnum.MEDIUM.getValue();
        }
        return difficulty;
    }

    private String resolveFinalAnswer(AiToolChatResult aiToolChatResult, InterviewToolCallContext context) {
        if (aiToolChatResult != null && aiToolChatResult.getAssistantMessage() != null
                && StrUtil.isNotBlank(aiToolChatResult.getAssistantMessage().getContent())) {
            return aiToolChatResult.getAssistantMessage().getContent();
        }
        if (StrUtil.isNotBlank(context.getFinalMessage())) {
            return context.getFinalMessage();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未返回最终回复");
    }

    private List<MockInterviewChatMessage> parseHistoryMessages(String historyMessage) {
        if (StrUtil.isBlank(historyMessage)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.parseArray(historyMessage).toList(MockInterviewChatMessage.class);
        } catch (Exception e) {
            log.warn("parse interview messages failed", e);
            return new ArrayList<>();
        }
    }

    private MockInterviewChatMessage buildUserHistoryMessage(String userMessage) {
        MockInterviewChatMessage chatMessage = new MockInterviewChatMessage();
        chatMessage.setRole(AiChatMessageRole.USER.getValue());
        chatMessage.setMessage(userMessage);
        return chatMessage;
    }

    private MockInterviewChatMessage buildAssistantHistoryMessage(String assistantMessage, InterviewToolCallContext context) {
        MockInterviewChatMessage chatMessage = new MockInterviewChatMessage();
        chatMessage.setRole(AiChatMessageRole.ASSISTANT.getValue());
        chatMessage.setMessage(assistantMessage);
        chatMessage.setToolName(context.getLastToolName());
        if (!context.isEnded() && context.getCurrentQuestionId() != null) {
            chatMessage.setQuestionId(context.getCurrentQuestionId());
            chatMessage.setQuestionTitle(context.getCurrentQuestionTitle());
        }
        return chatMessage;
    }

    private void saveInterviewProgress(Long mockInterviewId,
                                       Integer status,
                                       List<MockInterviewChatMessage> historyMessageList,
                                       MockInterviewReport report) {
        MockInterview updateMockInterview = new MockInterview();
        updateMockInterview.setId(mockInterviewId);
        updateMockInterview.setStatus(status);
        updateMockInterview.setMessages(JSONUtil.toJsonStr(historyMessageList));
        if (report != null) {
            updateMockInterview.setReport(writeJson(report));
        }
        boolean result = this.updateById(updateMockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "更新失败");
    }

    private JsonNode parseArguments(String arguments) {
        try {
            if (StringUtils.isBlank(arguments)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(arguments);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具参数解析失败");
        }
    }

    private String getNodeText(JsonNode jsonNode, String fieldName, String defaultValue) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asText(defaultValue);
    }

    private int getNodeInt(JsonNode jsonNode, String fieldName, int defaultValue) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asInt(defaultValue);
    }

    private long getRequiredLong(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        ThrowUtils.throwIf(fieldNode == null || fieldNode.isNull(), ErrorCode.PARAMS_ERROR, fieldName + " 不能为空");
        return fieldNode.asLong();
    }

    private List<String> getRequiredStringList(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        ThrowUtils.throwIf(fieldNode == null || !fieldNode.isArray() || fieldNode.size() == 0,
                ErrorCode.PARAMS_ERROR, fieldName + " 不能为空");
        List<String> result = new ArrayList<>();
        fieldNode.forEach(node -> {
            String value = StrUtil.trim(node.asText());
            if (StrUtil.isNotBlank(value)) {
                result.add(value);
            }
        });
        ThrowUtils.throwIf(CollUtil.isEmpty(result), ErrorCode.PARAMS_ERROR, fieldName + " 不能为空");
        return result;
    }

    private List<Long> extractQuestionIds(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isArray()) {
            return Collections.emptyList();
        }
        List<Long> questionIds = new ArrayList<>();
        jsonNode.forEach(node -> questionIds.add(node.asLong()));
        return questionIds;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON 序列化失败");
        }
    }

    private AiChatMessage buildMessage(AiChatMessageRole role, String content) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }
}
