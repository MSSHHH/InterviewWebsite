package com.yupi.mianshiya.manager;

import com.yupi.mianshiya.config.AiConfig;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.ai.AiChatMessage;
import com.yupi.mianshiya.model.dto.ai.AiChatMessageRole;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import com.yupi.mianshiya.model.dto.ai.AiToolDefinition;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 封装类测试
 */
@ExtendWith(MockitoExtension.class)
class AiManagerTest {

    @Mock
    private AiConfig aiConfig;

    @Mock
    private OpenAiChatModel openAiChatModel;

    @Spy
    @InjectMocks
    private AiManager aiManager;

    @BeforeEach
    void setUp() {
        lenient().when(aiConfig.getModel()).thenReturn("qwen-plus");
        lenient().when(aiConfig.getApiKey()).thenReturn("test-key");
        lenient().when(aiConfig.getBaseUrl()).thenReturn("https://example.com/v1");
        lenient().doReturn(openAiChatModel).when(aiManager).getChatModel(anyString());
    }

    @Test
    void doToolChatShouldBuildToolSpecifications() {
        AiToolDefinition toolDefinition = AiToolDefinition.builder()
                .name("submit_generated_questions")
                .description("提交结构化生成的面试题列表")
                .build();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("submit_generated_questions")
                .arguments("{\"questions\":[{\"title\":\"Redis 为什么快？\"}]}")
                .build();
        when(openAiChatModel.generate(anyList(), anyList()))
                .thenReturn(Response.from(AiMessage.from(toolExecutionRequest)));

        AiToolChatResult aiToolChatResult = aiManager.doToolChat(
                "system",
                "user",
                Collections.singletonList(toolDefinition),
                "submit_generated_questions"
        );

        ArgumentCaptor<List> toolSpecificationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(openAiChatModel).generate(anyList(), toolSpecificationsCaptor.capture());
        List<ToolSpecification> toolSpecifications = toolSpecificationsCaptor.getValue();
        assertEquals(1, toolSpecifications.size());
        assertEquals("submit_generated_questions", toolSpecifications.get(0).name());
        assertNotNull(aiToolChatResult.getToolCalls());
        assertEquals(1, aiToolChatResult.getToolCalls().size());
        assertEquals("submit_generated_questions", aiToolChatResult.getToolCalls().get(0).getName());
    }

    @Test
    void doToolChatShouldThrowWhenNoMessageReturned() {
        Response<AiMessage> emptyResponse = mock(Response.class);
        when(emptyResponse.content()).thenReturn(null);
        when(openAiChatModel.generate(anyList(), anyList()))
                .thenReturn(emptyResponse);

        BusinessException businessException = assertThrows(BusinessException.class, () ->
                aiManager.doToolChat("system", "user", Collections.<AiToolDefinition>emptyList(), "submit_generated_questions"));
        assertEquals("AI 工具调用失败，没有返回消息", businessException.getMessage());
    }

    @Test
    void doToolLoopChatShouldExecuteToolUntilAssistantReturnsText() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("search_questions_by_topic")
                .arguments("{\"topic\":\"Java 并发\",\"difficulty\":\"medium\"}")
                .build();
        when(openAiChatModel.generate(anyList(), anyList()))
                .thenReturn(Response.from(AiMessage.from(toolExecutionRequest)))
                .thenReturn(Response.from(AiMessage.from("请解释一下 synchronized 和 ReentrantLock 的区别。")));

        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.builder()
                .role(AiChatMessageRole.USER)
                .content("开始面试")
                .build());

        AiToolDefinition toolDefinition = AiToolDefinition.builder()
                .name("search_questions_by_topic")
                .description("搜索题目")
                .build();

        AiToolChatResult aiToolChatResult = aiManager.doToolLoopChat(
                messages,
                Collections.singletonList(toolDefinition),
                ignored -> "{\"questions\":[{\"id\":1,\"title\":\"线程池参数\"}]}",
                6,
                "qwen-plus"
        );

        assertEquals("请解释一下 synchronized 和 ReentrantLock 的区别。",
                aiToolChatResult.getAssistantMessage().getContent());
        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(openAiChatModel, org.mockito.Mockito.times(2)).generate(messagesCaptor.capture(), anyList());
        List secondRoundMessages = (List) messagesCaptor.getAllValues().get(1);
        boolean hasToolResultMessage = false;
        for (Object secondRoundMessage : secondRoundMessages) {
            ChatMessage chatMessage = (ChatMessage) secondRoundMessage;
            if (chatMessage.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                hasToolResultMessage = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasToolResultMessage);
    }
}
