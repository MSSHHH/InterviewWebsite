package com.yupi.mianshiya.controller;

import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.model.dto.questionBank.QuestionBankQueryRequest;
import com.yupi.mianshiya.model.entity.QuestionBank;
import com.yupi.mianshiya.model.vo.QuestionBankVO;
import com.yupi.mianshiya.service.QuestionBankService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankControllerTest {

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private UserService userService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<QuestionBankVO> questionBankVORBucket;

    @Mock
    private RLock rLock;

    @Mock
    private HttpServletRequest request;

    private TestableQuestionBankController questionBankController;

    @BeforeEach
    void setUp() {
        questionBankController = new TestableQuestionBankController();
        ReflectionTestUtils.setField(questionBankController, "questionBankService", questionBankService);
        ReflectionTestUtils.setField(questionBankController, "userService", userService);
        ReflectionTestUtils.setField(questionBankController, "redissonClient", redissonClient);
    }

    @Test
    void getQuestionBankVOById_shouldReturnRedisCacheWithoutQueryingDb() {
        QuestionBankQueryRequest queryRequest = buildQueryRequest(1L);
        QuestionBankVO cachedQuestionBankVO = buildQuestionBankVO(1L, "Redis 缓存题库");

        when(redissonClient.<QuestionBankVO>getBucket(anyString())).thenReturn(questionBankVORBucket);
        when(questionBankVORBucket.get()).thenReturn(cachedQuestionBankVO);

        BaseResponse<QuestionBankVO> response = questionBankController.getQuestionBankVOById(queryRequest, request);

        assertEquals(0, response.getCode());
        assertSame(cachedQuestionBankVO, response.getData());
        verify(questionBankService, never()).getById(anyLong());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void getQuestionBankVOById_shouldLoadFromDbAndBackfillRedisAndHotKey() throws Exception {
        QuestionBankQueryRequest queryRequest = buildQueryRequest(1L);
        QuestionBank questionBank = new QuestionBank();
        questionBank.setId(1L);
        questionBank.setTitle("系统设计题库");
        QuestionBankVO loadedQuestionBankVO = buildQuestionBankVO(1L, "系统设计题库");

        questionBankController.hotKeyEnabled = true;

        when(redissonClient.<QuestionBankVO>getBucket(anyString())).thenReturn(questionBankVORBucket);
        when(questionBankVORBucket.get()).thenReturn(null, null);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(2L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(questionBankService.getById(1L)).thenReturn(questionBank);
        when(questionBankService.getQuestionBankVO(questionBank, request)).thenReturn(loadedQuestionBankVO);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        BaseResponse<QuestionBankVO> firstResponse = questionBankController.getQuestionBankVOById(queryRequest, request);
        BaseResponse<QuestionBankVO> secondResponse = questionBankController.getQuestionBankVOById(queryRequest, request);

        assertEquals(0, firstResponse.getCode());
        assertSame(loadedQuestionBankVO, firstResponse.getData());
        assertEquals(0, secondResponse.getCode());
        assertSame(loadedQuestionBankVO, secondResponse.getData());
        verify(questionBankService, times(1)).getById(1L);
        verify(questionBankVORBucket, times(1)).set(loadedQuestionBankVO, 30 * 60L, TimeUnit.SECONDS);
        assertEquals("bank_detail_id=1", questionBankController.lastSmartSetKey);
        assertSame(loadedQuestionBankVO, questionBankController.lastSmartSetValue);
        assertTrue(questionBankController.hotKeyChecked);
    }

    @Test
    void getQuestionBankVOById_shouldQueryDbOnlyOnceUnderConcurrentRequests() throws Exception {
        QuestionBankQueryRequest queryRequest = buildQueryRequest(1L);
        QuestionBank questionBank = new QuestionBank();
        questionBank.setId(1L);
        questionBank.setTitle("并发题库");
        QuestionBankVO loadedQuestionBankVO = buildQuestionBankVO(1L, "并发题库");

        AtomicReference<QuestionBankVO> redisCache = new AtomicReference<>();
        AtomicBoolean dbLoadStarted = new AtomicBoolean(false);
        AtomicBoolean lockHeld = new AtomicBoolean(false);

        when(redissonClient.<QuestionBankVO>getBucket(anyString())).thenReturn(questionBankVORBucket);
        when(questionBankVORBucket.get()).thenAnswer(invocation -> redisCache.get());
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(2L, 10L, TimeUnit.SECONDS)).thenAnswer(invocation -> lockHeld.compareAndSet(false, true));
        when(rLock.isHeldByCurrentThread()).thenAnswer(invocation -> lockHeld.get());
        when(questionBankService.getById(1L)).thenAnswer(invocation -> {
            dbLoadStarted.set(true);
            Thread.sleep(150L);
            return questionBank;
        });
        when(questionBankService.getQuestionBankVO(questionBank, request)).thenReturn(loadedQuestionBankVO);
        doAnswer(invocation -> {
            redisCache.set(invocation.getArgument(0));
            return null;
        }).when(questionBankVORBucket).set(loadedQuestionBankVO, 30 * 60L, TimeUnit.SECONDS);
        doAnswer(invocation -> {
            lockHeld.set(false);
            return null;
        }).when(rLock).unlock();

        int threadCount = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<BaseResponse<QuestionBankVO>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return questionBankController.getQuestionBankVOById(queryRequest, request);
                }));
            }
            readyLatch.await(2, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<BaseResponse<QuestionBankVO>> future : futures) {
                BaseResponse<QuestionBankVO> response = future.get(5, TimeUnit.SECONDS);
                assertEquals(0, response.getCode());
                assertSame(loadedQuestionBankVO, response.getData());
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(dbLoadStarted.get());
        verify(questionBankService, times(1)).getById(1L);
        verify(questionBankService, times(1)).getQuestionBankVO(questionBank, request);
        verify(questionBankVORBucket, times(1)).set(loadedQuestionBankVO, 30 * 60L, TimeUnit.SECONDS);
    }

    private QuestionBankQueryRequest buildQueryRequest(Long id) {
        QuestionBankQueryRequest queryRequest = new QuestionBankQueryRequest();
        queryRequest.setId(id);
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        queryRequest.setNeedQueryQuestionList(false);
        return queryRequest;
    }

    private QuestionBankVO buildQuestionBankVO(Long id, String title) {
        QuestionBankVO questionBankVO = new QuestionBankVO();
        questionBankVO.setId(id);
        questionBankVO.setTitle(title);
        questionBankVO.setDescription(title + " 描述");
        return questionBankVO;
    }

    private static class TestableQuestionBankController extends QuestionBankController {

        private boolean hotKeyEnabled;

        private boolean hotKeyChecked;

        private String lastSmartSetKey;

        private QuestionBankVO lastSmartSetValue;

        @Override
        protected boolean isHotKey(String key) {
            hotKeyChecked = true;
            return hotKeyEnabled;
        }

        @Override
        protected QuestionBankVO getHotKeyValue(String key) {
            return null;
        }

        @Override
        protected void smartSetHotKey(String key, QuestionBankVO questionBankVO) {
            this.lastSmartSetKey = key;
            this.lastSmartSetValue = questionBankVO;
        }

        @Override
        protected void removeHotKey(String key) {
            // test no-op
        }
    }
}
