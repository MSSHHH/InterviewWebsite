package com.yupi.mianshiya.manager;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunction;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatTool;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.yupi.mianshiya.config.AiConfig;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 封装类测试
 */
@ExtendWith(MockitoExtension.class)
class AiManagerTest {

    @Mock
    private AiConfig aiConfig;

    @Spy
    @InjectMocks
    private AiManager aiManager;

    @BeforeEach
    void setUp() {
        lenient().when(aiConfig.getModel()).thenReturn("qwen-plus");
    }

    @Test
    void doToolChatShouldBuildRequestWithToolChoice() {
        ChatTool chatTool = new ChatTool();
        chatTool.setType("function");
        ChatFunction chatFunction = new ChatFunction();
        chatFunction.setName("submit_generated_questions");
        chatTool.setFunction(chatFunction);

        ChatFunctionCall chatFunctionCall = new ChatFunctionCall();
        chatFunctionCall.setName("submit_generated_questions");
        chatFunctionCall.setArguments("{\"questions\":[{\"title\":\"Redis 为什么快？\"}]}");
        ChatToolCall chatToolCall = new ChatToolCall();
        chatToolCall.setId("call_1");
        chatToolCall.setType("function");
        chatToolCall.setFunction(chatFunctionCall);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setToolCalls(Collections.singletonList(chatToolCall));

        ChatCompletionChoice chatCompletionChoice = new ChatCompletionChoice();
        chatCompletionChoice.setMessage(assistantMessage);

        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
        chatCompletionResult.setId("req_1");
        chatCompletionResult.setModel("qwen-plus");
        chatCompletionResult.setChoices(Collections.singletonList(chatCompletionChoice));
        doReturn(chatCompletionResult).when(aiManager).executeChatCompletion(any(ChatCompletionRequest.class));

        AiToolChatResult aiToolChatResult = aiManager.doToolChat("system", "user",
                Collections.singletonList(chatTool), "submit_generated_questions");

        ArgumentCaptor<ChatCompletionRequest> chatCompletionRequestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(aiManager).executeChatCompletion(chatCompletionRequestCaptor.capture());
        ChatCompletionRequest chatCompletionRequest = chatCompletionRequestCaptor.getValue();
        assertEquals(1, chatCompletionRequest.getTools().size());
        ChatCompletionRequest.ChatCompletionRequestToolChoice toolChoice =
                (ChatCompletionRequest.ChatCompletionRequestToolChoice) chatCompletionRequest.getToolChoice();
        assertEquals("function", toolChoice.getType());
        assertEquals("submit_generated_questions", toolChoice.getFunction().getName());
        assertEquals("req_1", aiToolChatResult.getRequestId());
        assertNotNull(aiToolChatResult.getToolCalls());
        assertEquals(1, aiToolChatResult.getToolCalls().size());
    }

    @Test
    void doToolChatShouldThrowWhenNoChoices() {
        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
        chatCompletionResult.setChoices(Collections.emptyList());
        doReturn(chatCompletionResult).when(aiManager).executeChatCompletion(any(ChatCompletionRequest.class));

        BusinessException businessException = assertThrows(BusinessException.class, () ->
                aiManager.doToolChat("system", "user", Collections.emptyList(), "submit_generated_questions"));
        assertEquals("AI 工具调用失败，没有返回结果", businessException.getMessage());
    }

    @Test
    void doToolLoopChatShouldExecuteToolUntilAssistantReturnsText() {
        ChatFunctionCall toolFunctionCall = new ChatFunctionCall();
        toolFunctionCall.setName("search_questions_by_topic");
        toolFunctionCall.setArguments("{\"topic\":\"Java 并发\",\"difficulty\":\"medium\"}");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("tool_call_1");
        toolCall.setType("function");
        toolCall.setFunction(toolFunctionCall);

        ChatMessage toolAssistantMessage = new ChatMessage();
        toolAssistantMessage.setRole(ChatMessageRole.ASSISTANT);
        toolAssistantMessage.setToolCalls(Collections.singletonList(toolCall));
        ChatCompletionChoice firstChoice = new ChatCompletionChoice();
        firstChoice.setMessage(toolAssistantMessage);
        ChatCompletionResult firstResult = new ChatCompletionResult();
        firstResult.setChoices(Collections.singletonList(firstChoice));
        firstResult.setId("req_tool");

        ChatMessage finalAssistantMessage = new ChatMessage();
        finalAssistantMessage.setRole(ChatMessageRole.ASSISTANT);
        finalAssistantMessage.setContent("请解释一下 synchronized 和 ReentrantLock 的区别。");
        ChatCompletionChoice secondChoice = new ChatCompletionChoice();
        secondChoice.setMessage(finalAssistantMessage);
        ChatCompletionResult secondResult = new ChatCompletionResult();
        secondResult.setChoices(Collections.singletonList(secondChoice));
        secondResult.setId("req_final");

        doReturn(firstResult, secondResult).when(aiManager)
                .executeChatCompletion(any(ChatCompletionRequest.class));

        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(ChatMessage.builder().role(ChatMessageRole.USER).content("开始面试").build());
        ChatTool chatTool = new ChatTool();
        chatTool.setType("function");
        chatTool.setFunction(new ChatFunction());

        AiToolChatResult aiToolChatResult = aiManager.doToolLoopChat(
                messages,
                Collections.singletonList(chatTool),
                ignored -> "{\"questions\":[{\"id\":1,\"title\":\"线程池参数\"}]}",
                6,
                "qwen-plus"
        );

        assertEquals("请解释一下 synchronized 和 ReentrantLock 的区别。",
                aiToolChatResult.getAssistantMessage().stringContent());
        ArgumentCaptor<ChatCompletionRequest> chatCompletionRequestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(aiManager, org.mockito.Mockito.times(2)).executeChatCompletion(chatCompletionRequestCaptor.capture());
        List<ChatCompletionRequest> chatCompletionRequests = chatCompletionRequestCaptor.getAllValues();
        List<ChatMessage> secondRoundMessages = chatCompletionRequests.get(1).getMessages();
        org.junit.jupiter.api.Assertions.assertTrue(
                secondRoundMessages.stream().anyMatch(message -> message.getRole() == ChatMessageRole.TOOL)
        );
    }
}
