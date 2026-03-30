package com.yupi.mianshiya.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionListCacheTest {

    @Mock
    private QuestionService questionService;

    @Mock
    private UserService userService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Page<QuestionVO>> questionPageBucket;

    @Mock
    private RLock rLock;

    @Mock
    private HttpServletRequest request;

    private TestableQuestionController questionController;

    @BeforeEach
    void setUp() {
        questionController = new TestableQuestionController();
        ReflectionTestUtils.setField(questionController, "questionService", questionService);
        ReflectionTestUtils.setField(questionController, "userService", userService);
        ReflectionTestUtils.setField(questionController, "redissonClient", redissonClient);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void listQuestionVOByPage_shouldLoadFromDbAndBackfillRedisAndHotKeyForFirstPage() throws Exception {
        QuestionQueryRequest queryRequest = buildFirstPageQueryRequest();
        Page<Question> questionPage = new Page<>(1, 20, 2);
        Page<QuestionVO> questionVOPage = buildQuestionVOPage();

        questionController.hotKeyEnabled = true;

        when(redissonClient.<Page<QuestionVO>>getBucket(anyString())).thenReturn(questionPageBucket);
        when(questionPageBucket.get()).thenReturn(null, null);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(2L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(questionService.listQuestionByPage(queryRequest)).thenReturn(questionPage);
        when(questionService.getQuestionVOPage(questionPage, request)).thenReturn(questionVOPage);

        BaseResponse<Page<QuestionVO>> response = questionController.listQuestionVOByPage(queryRequest, request);

        assertEquals(0, response.getCode());
        assertSame(questionVOPage, response.getData());
        verify(questionService, times(1)).listQuestionByPage(queryRequest);
        verify(questionPageBucket, times(1)).set(questionVOPage, 10 * 60L, TimeUnit.SECONDS);
        assertEquals("question_first_page:bankId=1|size=20|sortField=null|sortOrder=ascend", questionController.lastSmartSetKey);
        assertSame(questionVOPage, questionController.lastSmartSetValue);
        assertTrue(questionController.hotKeyChecked);
    }

    @Test
    void listQuestionVOByPage_shouldQueryDbOnlyOnceUnderConcurrentRequestsForFirstPage() throws Exception {
        QuestionQueryRequest queryRequest = buildFirstPageQueryRequest();
        Page<Question> questionPage = new Page<>(1, 20, 2);
        Page<QuestionVO> questionVOPage = buildQuestionVOPage();

        AtomicReference<Page<QuestionVO>> redisCache = new AtomicReference<>();
        AtomicBoolean dbLoadStarted = new AtomicBoolean(false);
        AtomicBoolean lockHeld = new AtomicBoolean(false);

        when(redissonClient.<Page<QuestionVO>>getBucket(anyString())).thenReturn(questionPageBucket);
        when(questionPageBucket.get()).thenAnswer(invocation -> redisCache.get());
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(2L, 10L, TimeUnit.SECONDS)).thenAnswer(invocation -> lockHeld.compareAndSet(false, true));
        when(rLock.isHeldByCurrentThread()).thenAnswer(invocation -> lockHeld.get());
        when(questionService.listQuestionByPage(queryRequest)).thenAnswer(invocation -> {
            dbLoadStarted.set(true);
            Thread.sleep(150L);
            return questionPage;
        });
        when(questionService.getQuestionVOPage(questionPage, request)).thenReturn(questionVOPage);
        doAnswer(invocation -> {
            redisCache.set(invocation.getArgument(0));
            return null;
        }).when(questionPageBucket).set(questionVOPage, 10 * 60L, TimeUnit.SECONDS);
        doAnswer(invocation -> {
            lockHeld.set(false);
            return null;
        }).when(rLock).unlock();

        int threadCount = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<BaseResponse<Page<QuestionVO>>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return questionController.listQuestionVOByPage(queryRequest, request);
                }));
            }
            readyLatch.await(2, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<BaseResponse<Page<QuestionVO>>> future : futures) {
                BaseResponse<Page<QuestionVO>> response = future.get(5, TimeUnit.SECONDS);
                assertEquals(0, response.getCode());
                assertSame(questionVOPage, response.getData());
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(dbLoadStarted.get());
        verify(questionService, times(1)).listQuestionByPage(queryRequest);
        verify(questionService, times(1)).getQuestionVOPage(questionPage, request);
        verify(questionPageBucket, times(1)).set(questionVOPage, 10 * 60L, TimeUnit.SECONDS);
    }

    private QuestionQueryRequest buildFirstPageQueryRequest() {
        QuestionQueryRequest queryRequest = new QuestionQueryRequest();
        queryRequest.setQuestionBankId(1L);
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(20);
        return queryRequest;
    }

    private Page<QuestionVO> buildQuestionVOPage() {
        Page<QuestionVO> questionVOPage = new Page<>(1, 20, 2);
        QuestionVO questionVO = new QuestionVO();
        questionVO.setId(1L);
        questionVO.setTitle("Spring Boot 自动配置原理");
        questionVOPage.setRecords(Arrays.asList(questionVO));
        return questionVOPage;
    }

    private static class TestableQuestionController extends QuestionController {

        private boolean hotKeyEnabled;

        private boolean hotKeyChecked;

        private String lastSmartSetKey;

        private Page<QuestionVO> lastSmartSetValue;

        @Override
        protected boolean isHotKey(String key) {
            hotKeyChecked = true;
            return hotKeyEnabled;
        }

        @Override
        protected Page<QuestionVO> getHotKeyQuestionListValue(String key) {
            return null;
        }

        @Override
        protected void smartSetHotKey(String key, Page<QuestionVO> questionVOPage) {
            this.lastSmartSetKey = key;
            this.lastSmartSetValue = questionVOPage;
        }

        @Override
        protected void removeHotKey(String key) {
            // test no-op
        }
    }
}
