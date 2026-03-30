package com.yupi.mianshiya.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.config.AiConfig;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.ai.AiChatMessage;
import com.yupi.mianshiya.model.dto.ai.AiChatMessageRole;
import com.yupi.mianshiya.model.dto.ai.AiToolCall;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import com.yupi.mianshiya.model.dto.ai.AiToolDefinition;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 通用的 AI 调用类
 */
@Service
@Slf4j
public class AiManager {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    @Resource
    private AiConfig aiConfig;

    @Resource
    private ObjectMapper objectMapper;

    private final ConcurrentMap<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * 调用 AI 接口，获取响应字符串
     */
    public String doChat(String userPrompt) {
        return doChat("", userPrompt, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串
     */
    public String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串
     */
    public String doChat(String systemPrompt, String userPrompt, String model) {
        List<AiChatMessage> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(buildMessage(AiChatMessageRole.SYSTEM, systemPrompt));
        }
        messages.add(buildMessage(AiChatMessageRole.USER, userPrompt));
        return doChat(messages, model);
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表，使用默认模型）
     */
    public String doChat(List<AiChatMessage> messages) {
        return doChat(messages, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表）
     */
    public String doChat(List<AiChatMessage> messages, String model) {
        Response<AiMessage> response = getChatModel(model).generate(toLangChainMessages(messages));
        AiMessage aiMessage = response.content();
        if (aiMessage == null || StrUtil.isBlank(aiMessage.text())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回文本内容");
        }
        return aiMessage.text();
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(String systemPrompt, String userPrompt, List<AiToolDefinition> tools, String toolName) {
        return doToolChat(systemPrompt, userPrompt, tools, toolName, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(String systemPrompt, String userPrompt, List<AiToolDefinition> tools, String toolName, String model) {
        List<AiChatMessage> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(buildMessage(AiChatMessageRole.SYSTEM, systemPrompt));
        }
        messages.add(buildMessage(AiChatMessageRole.USER, userPrompt));
        return doToolChat(messages, tools, toolName, model);
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(List<AiChatMessage> messages, List<AiToolDefinition> tools, String toolName, String model) {
        Response<AiMessage> response = getChatModel(model).generate(
                toLangChainMessages(messages),
                toToolSpecifications(tools)
        );
        return buildToolChatResult(response, model);
    }

    public String getDefaultModel() {
        String model = aiConfig.getModel();
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 默认模型未配置");
        }
        return model.trim();
    }

    /**
     * 多轮工具调用，直到模型返回最终文本。
     */
    public AiToolChatResult doToolLoopChat(List<AiChatMessage> messages,
                                           List<AiToolDefinition> tools,
                                           Function<AiToolCall, String> toolExecutor,
                                           int maxRounds,
                                           String model) {
        List<ChatMessage> conversation = new ArrayList<>(toLangChainMessages(messages));
        List<ToolSpecification> toolSpecifications = toToolSpecifications(tools);
        for (int round = 0; round < maxRounds; round++) {
            Response<AiMessage> response = getChatModel(model).generate(conversation, toolSpecifications);
            AiToolChatResult aiToolChatResult = buildToolChatResult(response, model);
            AiMessage aiMessage = response.content();
            if (aiMessage != null) {
                conversation.add(aiMessage);
            }
            if (CollUtil.isEmpty(aiToolChatResult.getToolCalls())) {
                if (aiToolChatResult.getAssistantMessage() != null
                        && StrUtil.isNotBlank(aiToolChatResult.getAssistantMessage().getContent())) {
                    return aiToolChatResult;
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，没有返回文本内容");
            }
            for (AiToolCall toolCall : aiToolChatResult.getToolCalls()) {
                String toolResult = toolExecutor.apply(toolCall);
                conversation.add(ToolExecutionResultMessage.from(toolCall.getName(), toolResult));
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，超过最大工具轮次");
    }

    protected OpenAiChatModel getChatModel(String modelName) {
        validateAiConfig();
        return chatModelCache.computeIfAbsent(modelName, key -> OpenAiChatModel.builder()
                .baseUrl(aiConfig.getBaseUrl().trim())
                .apiKey(aiConfig.getApiKey().trim())
                .modelName(key)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build());
    }

    private void validateAiConfig() {
        if (StrUtil.isBlank(aiConfig.getApiKey())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI API Key 未配置");
        }
        if (StrUtil.isBlank(aiConfig.getBaseUrl())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI Base URL 未配置");
        }
    }

    private AiChatMessage buildMessage(AiChatMessageRole role, String content) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    private List<ChatMessage> toLangChainMessages(List<AiChatMessage> messages) {
        if (CollUtil.isEmpty(messages)) {
            return Collections.emptyList();
        }
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (AiChatMessage message : messages) {
            if (message == null || message.getRole() == null || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            switch (message.getRole()) {
                case SYSTEM:
                    result.add(SystemMessage.from(message.getContent()));
                    break;
                case USER:
                    result.add(UserMessage.from(message.getContent()));
                    break;
                case ASSISTANT:
                    result.add(AiMessage.from(message.getContent()));
                    break;
                default:
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的 AI 消息角色");
            }
        }
        return result;
    }

    private List<ToolSpecification> toToolSpecifications(List<AiToolDefinition> tools) {
        if (CollUtil.isEmpty(tools)) {
            return Collections.emptyList();
        }
        List<ToolSpecification> result = new ArrayList<>(tools.size());
        for (AiToolDefinition tool : tools) {
            result.add(ToolSpecification.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .parameters(toToolParameters(tool.getParameters()))
                    .build());
        }
        return result;
    }

    private ToolParameters toToolParameters(JsonNode parametersNode) {
        if (parametersNode == null || parametersNode.isNull()) {
            return ToolParameters.builder()
                    .type("object")
                    .properties(Collections.<String, Map<String, Object>>emptyMap())
                    .required(Collections.<String>emptyList())
                    .build();
        }
        String type = getText(parametersNode, "type", "object");
        Map<String, Map<String, Object>> properties = toProperties(parametersNode.get("properties"));
        List<String> required = toStringList(parametersNode.get("required"));
        return ToolParameters.builder()
                .type(type)
                .properties(properties)
                .required(required)
                .build();
    }

    private Map<String, Map<String, Object>> toProperties(JsonNode propertiesNode) {
        if (propertiesNode == null || !propertiesNode.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        propertiesNode.fields().forEachRemaining(entry -> properties.put(entry.getKey(), toObjectMap(entry.getValue())));
        return properties;
    }

    private Map<String, Object> toObjectMap(JsonNode jsonNode) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (jsonNode == null || jsonNode.isNull() || !jsonNode.isObject()) {
            return result;
        }
        jsonNode.fields().forEachRemaining(entry -> result.put(entry.getKey(), toJavaValue(entry.getValue())));
        return result;
    }

    private Object toJavaValue(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        if (jsonNode.isObject()) {
            return toObjectMap(jsonNode);
        }
        if (jsonNode.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : jsonNode) {
                list.add(toJavaValue(item));
            }
            return list;
        }
        if (jsonNode.isTextual()) {
            return jsonNode.asText();
        }
        if (jsonNode.isIntegralNumber()) {
            return jsonNode.asLong();
        }
        if (jsonNode.isFloatingPointNumber()) {
            return jsonNode.asDouble();
        }
        if (jsonNode.isBoolean()) {
            return jsonNode.asBoolean();
        }
        return jsonNode.toString();
    }

    private List<String> toStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item != null && item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String getText(JsonNode jsonNode, String fieldName, String defaultValue) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asText(defaultValue);
    }

    private AiToolChatResult buildToolChatResult(Response<AiMessage> response, String model) {
        AiMessage aiMessage = response.content();
        if (aiMessage == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，没有返回消息");
        }
        AiToolChatResult result = new AiToolChatResult();
        result.setRequestId(null);
        result.setModel(model);
        result.setAssistantMessage(AiChatMessage.builder()
                .role(AiChatMessageRole.ASSISTANT)
                .content(aiMessage.text())
                .build());
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequest();
        if (toolExecutionRequest != null) {
            AiToolCall toolCall = AiToolCall.builder()
                    .id(null)
                    .name(toolExecutionRequest.name())
                    .arguments(toolExecutionRequest.arguments())
                    .build();
            result.setToolCalls(Collections.singletonList(toolCall));
        } else {
            result.setToolCalls(Collections.<AiToolCall>emptyList());
        }
        if (log.isDebugEnabled()) {
            log.debug("langchain4j ai response model={}, hasToolCall={}, textPresent={}",
                    model,
                    toolExecutionRequest != null,
                    StrUtil.isNotBlank(aiMessage.text()));
        }
        return result;
    }
}
