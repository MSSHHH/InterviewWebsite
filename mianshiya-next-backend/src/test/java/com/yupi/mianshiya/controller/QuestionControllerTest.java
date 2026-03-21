package com.yupi.mianshiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QuestionController 测试类
 * 测试 /get/vo 接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionControllerTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private WebApplicationContext webApplicationContext;

    @MockBean
    private QuestionService questionService;

    @MockBean
    private UserService userService;

    @Resource
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 如果 MockMvc 没有自动注入，可以手动构建
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        }
    }

    /**
     * 测试成功获取题目 VO
     */
    @Test
    void testGetQuestionVOById_Success() throws Exception {
        // 准备测试数据
        long questionId = 1L;
        Question question = new Question();
        question.setId(questionId);
        question.setTitle("测试题目");
        question.setContent("这是测试内容");

        QuestionVO questionVO = new QuestionVO();
        questionVO.setId(questionId);
        questionVO.setTitle("测试题目");
        questionVO.setContent("这是测试内容");

        // Mock 服务层方法
        when(questionService.getById(questionId)).thenReturn(question);
        when(questionService.getQuestionVO(any(Question.class), any())).thenReturn(questionVO);
        when(userService.getLoginUserPermitNull(any())).thenReturn(null);

        // 执行请求并验证
        mockMvc.perform(get("/api/question/get/vo")
                        .param("id", String.valueOf(questionId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print()) // 打印请求和响应信息
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(questionId))
                .andExpect(jsonPath("$.data.title").value("测试题目"));
    }

    /**
     * 测试参数错误 - id 小于等于 0
     */
    @Test
    void testGetQuestionVOById_InvalidId() throws Exception {
        long invalidId = 0L;

        mockMvc.perform(get("/api/question/get/vo")
                        .param("id", String.valueOf(invalidId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000)); // PARAMS_ERROR
    }

    /**
     * 测试题目不存在
     */
    @Test
    void testGetQuestionVOById_NotFound() throws Exception {
        long questionId = 999L;

        // Mock 服务层返回 null
        when(questionService.getById(questionId)).thenReturn(null);
        when(userService.getLoginUserPermitNull(any())).thenReturn(null);

        mockMvc.perform(get("/api/question/get/vo")
                        .param("id", String.valueOf(questionId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400)); // NOT_FOUND_ERROR
    }

    /**
     * 测试已登录用户获取题目 VO
     */
    @Test
    void testGetQuestionVOById_WithLoginUser() throws Exception {
        // 准备测试数据
        long questionId = 1L;
        long userId = 100L;

        Question question = new Question();
        question.setId(questionId);
        question.setTitle("测试题目");

        QuestionVO questionVO = new QuestionVO();
        questionVO.setId(questionId);
        questionVO.setTitle("测试题目");

        User loginUser = new User();
        loginUser.setId(userId);

        // Mock 服务层方法
        when(questionService.getById(questionId)).thenReturn(question);
        when(questionService.getQuestionVO(any(Question.class), any())).thenReturn(questionVO);
        when(userService.getLoginUserPermitNull(any())).thenReturn(loginUser);

        // 执行请求并验证
        mockMvc.perform(get("/api/question/get/vo")
                        .param("id", String.valueOf(questionId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(questionId));
    }

    /**
     * 测试缺少 id 参数
     */
    @Test
    void testGetQuestionVOById_MissingId() throws Exception {
        mockMvc.perform(get("/api/question/get/vo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest()); // 400 Bad Request
    }
}
