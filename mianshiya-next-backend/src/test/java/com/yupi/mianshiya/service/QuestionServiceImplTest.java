package com.yupi.mianshiya.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.model.dto.ai.AiToolChatResult;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.service.impl.QuestionServiceImpl;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Spy
    @InjectMocks
    private QuestionServiceImpl questionService;

    @Mock
    private AiManager aiManager;

    @Mock
    private UserService userService;

    @Mock
    private QuestionBankQuestionService questionBankQuestionService;

    @Mock
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(questionService, "objectMapper", new ObjectMapper());
    }

    @Test
    void aiGenerateQuestionsShouldSaveStructuredQuestions() {
        User user = new User();
        user.setId(123L);
        when(aiManager.doToolChat(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\" Redis 为什么快？ \"},{\"title\":\"Redis 持久化机制有哪些？\"}]}"));
        when(aiManager.doChat(anyString(), anyString())).thenReturn("markdown answer");
        doReturn(true).when(questionService).saveBatch(anyCollection());

        boolean result = questionService.aiGenerateQuestions("Redis", 2, user);

        assertEquals(true, result);
        ArgumentCaptor<Collection<Question>> questionCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(questionService).saveBatch(questionCaptor.capture());
        Collection<Question> questionCollection = questionCaptor.getValue();
        assertEquals(2, questionCollection.size());
        Question firstQuestion = questionCollection.iterator().next();
        assertEquals(123L, firstQuestion.getUserId());
        assertEquals("[\"待审核\"]", firstQuestion.getTags());
        assertEquals("markdown answer", firstQuestion.getAnswer());
        assertEquals("Redis 为什么快？", firstQuestion.getTitle());
    }

    @Test
    void aiGenerateQuestionsShouldRetryWhenFirstToolResultInvalid() {
        User user = new User();
        user.setId(123L);
        when(aiManager.doToolChat(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\"Redis 为什么快？\"},{\"title\":\"Redis 为什么快？\"}]}"))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\"Redis 为什么快？\"},{\"title\":\"Redis 如何保证高可用？\"}]}"));
        when(aiManager.doChat(anyString(), anyString())).thenReturn("markdown answer");
        doReturn(true).when(questionService).saveBatch(anyCollection());

        boolean result = questionService.aiGenerateQuestions("Redis", 2, user);

        assertEquals(true, result);
        verify(aiManager, times(2)).doToolChat(anyString(), anyString(), anyList(), eq("submit_generated_questions"));
        verify(questionService).saveBatch(anyCollection());
    }

    @Test
    void aiGenerateQuestionsShouldFailWhenToolResultInvalidAfterRetry() {
        User user = new User();
        user.setId(123L);
        when(aiManager.doToolChat(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\"Redis 为什么快？\"}]}"))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\"Redis 为什么快？\"}]}"));

        BusinessException businessException = assertThrows(BusinessException.class,
                () -> questionService.aiGenerateQuestions("Redis", 2, user));

        assertEquals("AI 生成题目失败，结构化结果校验未通过", businessException.getMessage());
        verify(questionService, never()).saveBatch(anyCollection());
    }

    @Test
    void aiGenerateQuestionsShouldNotSaveWhenAnswerGenerationFails() {
        User user = new User();
        user.setId(123L);
        when(aiManager.doToolChat(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(buildToolChatResult("{\"questions\":[{\"title\":\"Redis 为什么快？\"},{\"title\":\"Redis 如何保证高可用？\"}]}"));
        when(aiManager.doChat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败"));

        assertThrows(BusinessException.class, () -> questionService.aiGenerateQuestions("Redis", 2, user));
        verify(questionService, never()).saveBatch(anyCollection());
    }

    private AiToolChatResult buildToolChatResult(String arguments) {
        ChatFunctionCall chatFunctionCall = new ChatFunctionCall();
        chatFunctionCall.setName("submit_generated_questions");
        chatFunctionCall.setArguments(arguments);

        ChatToolCall chatToolCall = new ChatToolCall();
        chatToolCall.setId("call_1");
        chatToolCall.setType("function");
        chatToolCall.setFunction(chatFunctionCall);

        AiToolChatResult aiToolChatResult = new AiToolChatResult();
        aiToolChatResult.setRequestId("req_1");
        aiToolChatResult.setModel("deepseek-v3-241226");
        aiToolChatResult.setToolCalls(Collections.singletonList(chatToolCall));
        return aiToolChatResult;
    }
}
