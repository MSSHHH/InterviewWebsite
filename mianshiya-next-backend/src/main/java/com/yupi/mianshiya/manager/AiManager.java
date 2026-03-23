package com.yupi.mianshiya.manager;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatTool;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.config.AiConfig;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用的 AI 调用类
 */
@Service
public class AiManager {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Resource
    private AiConfig aiConfig;

    @Resource
    private ObjectMapper objectMapper;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param userPrompt
     * @return
     */
    public String doChat(String userPrompt) {
        return doChat("", userPrompt, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param systemPrompt
     * @param userPrompt
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param systemPrompt
     * @param userPrompt
     * @param model
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt, String model) {
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doChat(messages, model);
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表，使用默认模型）
     *
     * @param messages
     * @return
     */
    public String doChat(List<ChatMessage> messages) {
        return doChat(messages, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表）
     *
     * @param messages
     * @param model
     * @return
     */
    public String doChat(List<ChatMessage> messages, String model) {
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();
        ChatCompletionResult chatCompletionResult = executeChatCompletion(chatCompletionRequest);
        List<ChatCompletionChoice> choices = chatCompletionResult.getChoices();
        if (CollUtil.isNotEmpty(choices)) {
            ChatMessage chatMessage = choices.get(0).getMessage();
            if (chatMessage == null || chatMessage.getContent() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回文本内容");
            }
            return chatMessage.stringContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(String systemPrompt, String userPrompt, List<ChatTool> tools, String toolName) {
        return doToolChat(systemPrompt, userPrompt, tools, toolName, getDefaultModel());
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(String systemPrompt, String userPrompt, List<ChatTool> tools, String toolName, String model) {
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doToolChat(messages, tools, toolName, model);
    }

    /**
     * 调用 AI 接口，获取工具调用结果
     */
    public AiToolChatResult doToolChat(List<ChatMessage> messages, List<ChatTool> tools, String toolName, String model) {
        ChatCompletionRequest.ChatCompletionRequestToolChoiceFunction toolChoiceFunction =
                new ChatCompletionRequest.ChatCompletionRequestToolChoiceFunction(toolName);
        ChatCompletionRequest.ChatCompletionRequestToolChoice toolChoice =
                new ChatCompletionRequest.ChatCompletionRequestToolChoice("function", toolChoiceFunction);
        ChatCompletionResult chatCompletionResult = executeChatCompletion(buildToolRequest(messages, tools, model, toolChoice));
        return buildToolChatResult(chatCompletionResult);
    }

    public String getDefaultModel() {
        String model = aiConfig.getModel();
        if (model == null || model.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 默认模型未配置");
        }
        return model.trim();
    }

    /**
     * 多轮工具调用，直到模型返回最终文本。
     */
    public AiToolChatResult doToolLoopChat(List<ChatMessage> messages,
                                           List<ChatTool> tools,
                                           Function<ChatToolCall, String> toolExecutor,
                                           int maxRounds,
                                           String model) {
        List<ChatMessage> conversation = new ArrayList<>(messages);
        for (int round = 0; round < maxRounds; round++) {
            ChatCompletionResult chatCompletionResult = executeChatCompletion(buildToolRequest(conversation, tools, model, null));
            AiToolChatResult aiToolChatResult = buildToolChatResult(chatCompletionResult);
            ChatMessage assistantMessage = aiToolChatResult.getAssistantMessage();
            conversation.add(assistantMessage);
            if (CollUtil.isEmpty(aiToolChatResult.getToolCalls())) {
                if (assistantMessage.getContent() != null) {
                    return aiToolChatResult;
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，没有返回文本内容");
            }
            for (ChatToolCall toolCall : aiToolChatResult.getToolCalls()) {
                String toolResult = toolExecutor.apply(toolCall);
                ChatMessage toolMessage = ChatMessage.builder()
                        .role(ChatMessageRole.TOOL)
                        .toolCallId(toolCall.getId())
                        .content(toolResult)
                        .build();
                conversation.add(toolMessage);
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，超过最大工具轮次");
    }

    protected ChatCompletionResult executeChatCompletion(ChatCompletionRequest chatCompletionRequest) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI API Key 未配置");
        }
        String baseUrl = aiConfig.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI Base URL 未配置");
        }
        String requestUrl = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        try {
            RequestBody requestBody = RequestBody.create(buildRequestJson(chatCompletionRequest), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", "Bearer " + apiKey.trim())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            String.format("AI 调用失败（HTTP %d）：%s", response.code(), responseBody));
                }
                return parseChatCompletionResult(responseBody);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 调用失败：" + e.getMessage());
        }
    }

    private ChatCompletionRequest buildToolRequest(List<ChatMessage> messages,
                                                   List<ChatTool> tools,
                                                   String model,
                                                   ChatCompletionRequest.ChatCompletionRequestToolChoice toolChoice) {
        if (toolChoice != null) {
            return ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .tools(tools)
                    .toolChoice(toolChoice)
                    .build();
        }
        return ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .tools(tools)
                .build();
    }

    private String buildRequestJson(ChatCompletionRequest chatCompletionRequest) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", chatCompletionRequest.getModel());
        root.set("messages", buildMessagesNode(chatCompletionRequest.getMessages()));
        if (CollUtil.isNotEmpty(chatCompletionRequest.getTools())) {
            root.set("tools", buildToolsNode(chatCompletionRequest.getTools()));
        }
        if (chatCompletionRequest.getToolChoice() != null) {
            root.set("tool_choice", buildToolChoiceNode(
                    (ChatCompletionRequest.ChatCompletionRequestToolChoice) chatCompletionRequest.getToolChoice()));
        }
        return objectMapper.writeValueAsString(root);
    }

    private ArrayNode buildMessagesNode(List<ChatMessage> messages) {
        ArrayNode messageArray = objectMapper.createArrayNode();
        if (CollUtil.isEmpty(messages)) {
            return messageArray;
        }
        for (ChatMessage message : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", message.getRole().value());
            String content = message.stringContent();
            if (content != null) {
                messageNode.put("content", content);
            } else {
                messageNode.putNull("content");
            }
            if (message.getToolCallId() != null) {
                messageNode.put("tool_call_id", message.getToolCallId());
            }
            if (CollUtil.isNotEmpty(message.getToolCalls())) {
                ArrayNode toolCallsNode = objectMapper.createArrayNode();
                for (ChatToolCall toolCall : message.getToolCalls()) {
                    toolCallsNode.add(buildToolCallNode(toolCall));
                }
                messageNode.set("tool_calls", toolCallsNode);
            }
            messageArray.add(messageNode);
        }
        return messageArray;
    }

    private ArrayNode buildToolsNode(List<ChatTool> tools) {
        ArrayNode toolsArray = objectMapper.createArrayNode();
        for (ChatTool tool : tools) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", tool.getType());
            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", tool.getFunction().getName());
            if (tool.getFunction().getDescription() != null) {
                functionNode.put("description", tool.getFunction().getDescription());
            }
            JsonNode parametersNode = objectMapper.valueToTree(tool.getFunction().getParameters());
            functionNode.set("parameters", parametersNode);
            toolNode.set("function", functionNode);
            toolsArray.add(toolNode);
        }
        return toolsArray;
    }

    private ObjectNode buildToolChoiceNode(ChatCompletionRequest.ChatCompletionRequestToolChoice toolChoice) {
        ObjectNode toolChoiceNode = objectMapper.createObjectNode();
        toolChoiceNode.put("type", toolChoice.getType());
        if (toolChoice.getFunction() != null) {
            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", toolChoice.getFunction().getName());
            toolChoiceNode.set("function", functionNode);
        }
        return toolChoiceNode;
    }

    private ObjectNode buildToolCallNode(ChatToolCall toolCall) {
        ObjectNode toolCallNode = objectMapper.createObjectNode();
        toolCallNode.put("id", toolCall.getId());
        toolCallNode.put("type", toolCall.getType());
        if (toolCall.getFunction() != null) {
            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", toolCall.getFunction().getName());
            functionNode.put("arguments", toolCall.getFunction().getArguments());
            toolCallNode.set("function", functionNode);
        }
        return toolCallNode;
    }

    private ChatCompletionResult parseChatCompletionResult(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choicesNode = root.get("choices");
        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
        if (root.hasNonNull("id")) {
            chatCompletionResult.setId(root.get("id").asText());
        }
        if (root.hasNonNull("model")) {
            chatCompletionResult.setModel(root.get("model").asText());
        }
        if (choicesNode == null || !choicesNode.isArray()) {
            chatCompletionResult.setChoices(new ArrayList<>());
            return chatCompletionResult;
        }
        List<ChatCompletionChoice> choices = new ArrayList<>();
        for (JsonNode choiceNode : choicesNode) {
            ChatCompletionChoice choice = new ChatCompletionChoice();
            JsonNode messageNode = choiceNode.get("message");
            if (messageNode != null && !messageNode.isNull()) {
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setRole(ChatMessageRole.ASSISTANT);
                JsonNode contentNode = messageNode.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    if (contentNode.isTextual()) {
                        chatMessage.setContent(contentNode.asText());
                    } else {
                        chatMessage.setContent(objectMapper.writeValueAsString(contentNode));
                    }
                }
                JsonNode toolCallsNode = messageNode.get("tool_calls");
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    List<ChatToolCall> toolCalls = new ArrayList<>();
                    for (JsonNode toolCallNode : toolCallsNode) {
                        ChatFunctionCall functionCall = new ChatFunctionCall();
                        JsonNode functionNode = toolCallNode.get("function");
                        if (functionNode != null && !functionNode.isNull()) {
                            if (functionNode.hasNonNull("name")) {
                                functionCall.setName(functionNode.get("name").asText());
                            }
                            if (functionNode.has("arguments") && !functionNode.get("arguments").isNull()) {
                                JsonNode argumentsNode = functionNode.get("arguments");
                                if (argumentsNode.isTextual()) {
                                    functionCall.setArguments(argumentsNode.asText());
                                } else {
                                    functionCall.setArguments(objectMapper.writeValueAsString(argumentsNode));
                                }
                            }
                        }
                        ChatToolCall toolCall = new ChatToolCall();
                        if (toolCallNode.hasNonNull("id")) {
                            toolCall.setId(toolCallNode.get("id").asText());
                        }
                        if (toolCallNode.hasNonNull("type")) {
                            toolCall.setType(toolCallNode.get("type").asText());
                        }
                        toolCall.setFunction(functionCall);
                        toolCalls.add(toolCall);
                    }
                    chatMessage.setToolCalls(toolCalls);
                }
                choice.setMessage(chatMessage);
            }
            choices.add(choice);
        }
        chatCompletionResult.setChoices(choices);
        return chatCompletionResult;
    }

    private AiToolChatResult buildToolChatResult(ChatCompletionResult chatCompletionResult) {
        List<ChatCompletionChoice> choices = chatCompletionResult.getChoices();
        if (CollUtil.isEmpty(choices)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，没有返回结果");
        }
        ChatMessage assistantMessage = choices.get(0).getMessage();
        if (assistantMessage == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 工具调用失败，没有返回消息");
        }
        AiToolChatResult aiToolChatResult = new AiToolChatResult();
        aiToolChatResult.setRequestId(chatCompletionResult.getId());
        aiToolChatResult.setModel(chatCompletionResult.getModel());
        aiToolChatResult.setAssistantMessage(assistantMessage);
        aiToolChatResult.setToolCalls(assistantMessage.getToolCalls());
        return aiToolChatResult;
    }
}
