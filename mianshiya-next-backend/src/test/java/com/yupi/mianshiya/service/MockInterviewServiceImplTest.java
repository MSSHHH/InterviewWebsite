package com.yupi.mianshiya.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.model.dto.mockinterview.InterviewToolCallContext;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewAddRequest;
import com.yupi.mianshiya.model.dto.mockinterview.MockInterviewReport;
import com.yupi.mianshiya.model.entity.MockInterview;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.service.impl.MockInterviewServiceImpl;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceImplTest {

    @Spy
    @InjectMocks
    private MockInterviewServiceImpl mockInterviewService;

    @Mock
    private AiManager aiManager;

    @Mock
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mockInterviewService, "objectMapper", new ObjectMapper());
    }

    @Test
    void createMockInterviewShouldRejectInvalidDifficulty() {
        MockInterviewAddRequest request = new MockInterviewAddRequest();
        request.setWorkExperience("3 年");
        request.setJobPosition("Java 开发工程师");
        request.setTopic("Java 并发");
        request.setDifficulty("expert");
        User user = new User();
        user.setId(1L);

        BusinessException businessException = assertThrows(BusinessException.class,
                () -> mockInterviewService.createMockInterview(request, user));

        assertEquals("面试难度非法", businessException.getMessage());
    }

    @Test
    void finishInterviewToolShouldPersistReport() {
        MockInterview mockInterview = new MockInterview();
        mockInterview.setId(1L);
        InterviewToolCallContext context = new InterviewToolCallContext();
        context.getAskedQuestionIds().add(101L);
        context.getAskedQuestionIds().add(102L);
        doReturn(true).when(mockInterviewService).updateById(any(MockInterview.class));

        ChatFunctionCall functionCall = new ChatFunctionCall();
        functionCall.setName("finish_interview_and_generate_report");
        functionCall.setArguments("{\"overallScore\":85,\"summary\":\"整体表现稳定\",\"strengths\":[\"基础扎实\"],\"weaknesses\":[\"表达略慢\"],\"suggestions\":[\"增加高并发实战\"],\"finalMessage\":\"这场面试先到这里。\"}");
        ChatToolCall chatToolCall = new ChatToolCall();
        chatToolCall.setId("tool_1");
        chatToolCall.setType("function");
        chatToolCall.setFunction(functionCall);

        String toolResult = ReflectionTestUtils.invokeMethod(
                mockInterviewService,
                "executeInterviewToolCall",
                chatToolCall,
                mockInterview,
                context
        );

        assertNotNull(toolResult);
        assertEquals(true, context.isEnded());
        assertEquals("这场面试先到这里。", context.getFinalMessage());
        MockInterviewReport report = context.getReport();
        assertNotNull(report);
        assertEquals(85, report.getOverallScore());
        assertEquals(2, report.getQuestionIds().size());
        verify(mockInterviewService).updateById(any(MockInterview.class));
    }
}
