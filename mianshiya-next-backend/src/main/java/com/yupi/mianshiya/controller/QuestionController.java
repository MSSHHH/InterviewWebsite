package com.yupi.mianshiya.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.DeleteRequest;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.constant.RedisConstant;
import com.yupi.mianshiya.constant.UserConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.manager.CounterManager;
import com.yupi.mianshiya.model.dto.question.*;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.enums.QuestionDifficultyEnum;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.sentinel.SentinelConstant;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 题目控制器（刷题平台最核心的业务控制器）。
 *
 * 主要职责：
 * 1) 提供题目 CRUD 与分页查询；
 * 2) 提供 ES 搜索入口，并在 ES 异常时自动降级到 MySQL；
 * 3) 提供管理员批量删除、AI 生成题目等扩展能力；
 * 4) 接入 Sentinel / 频控管理器，对高频访问做防刷与保护。
 *
 * 说明：
 * - 题目检索是性能关键路径，控制器会优先引导到 ES；
 * - 题目详情与列表返回 VO，由 Service 统一封装用户信息等扩展字段；
 * - 权限策略：普通用户可读，写操作按“本人或管理员”校验。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    private static final long QUESTION_LIST_REDIS_TTL_SECONDS = 10 * 60L;

    private static final long QUESTION_LIST_LOCK_WAIT_SECONDS = 2L;

    private static final long QUESTION_LIST_LOCK_LEASE_SECONDS = 10L;

    private static final int QUESTION_LIST_RETRY_TIMES = 3;

    private static final long QUESTION_LIST_RETRY_MILLIS = 100L;

    /**
     * 记录当前节点已经写入过的题库第一页 HotKey。
     */
    private final Set<String> questionFirstPageHotKeys = ConcurrentHashMap.newKeySet();

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        if (StrUtil.isBlank(question.getDifficulty())) {
            question.setDifficulty(QuestionDifficultyEnum.MEDIUM.getValue());
        }
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        clearQuestionListCaches();
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        clearQuestionListCaches();
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        if (StrUtil.isBlank(question.getDifficulty())) {
            question.setDifficulty(QuestionDifficultyEnum.MEDIUM.getValue());
        }
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        clearQuestionListCaches();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 检测和处置爬虫（可以自行扩展为 - 登录后才能获取到答案）
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            crawlerDetect(loginUser.getId());
        }
        // 友情提示，对于敏感的内容，可以再打印一些日志，记录用户访问的内容
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    // 仅是为了方便，才把这段代码写到这里
    @Resource
    private CounterManager counterManager;

    /**
     * 检测爬虫
     *
     * @param loginUserId
     */
    private void crawlerDetect(long loginUserId) {
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 调用多少次时封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 统计一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
        // 是否封号
        if (count > BAN_COUNT) {
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            User updateUser = new User();
            updateUser.setId(loginUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告：访问太频繁");
        }
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        return doListQuestionVOByPageWithSentinel(questionQueryRequest, request);
    }

    /**
     * 分页获取题目列表（封装类 - 限流版）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/sentinel")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                       HttpServletRequest request) {
        return doListQuestionVOByPageWithSentinel(questionQueryRequest, request);
    }

    private BaseResponse<Page<QuestionVO>> doListQuestionVOByPageWithSentinel(QuestionQueryRequest questionQueryRequest,
                                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        String listCacheKey = buildQuestionListCacheKey(questionQueryRequest);
        // 基于 IP 限流
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConstant.listQuestionVOByPage, EntryType.IN, 1, remoteAddr);
            String hotKey = buildQuestionFirstPageHotKey(questionQueryRequest);
            if (hotKey != null && isHotKey(hotKey)) {
                Page<QuestionVO> hotCachedPage = getHotKeyQuestionListValue(hotKey);
                if (hotCachedPage != null) {
                    return ResultUtils.success(hotCachedPage);
                }
            }

            String redisKey = RedisConstant.getQuestionListRedisKey(listCacheKey);
            Page<QuestionVO> redisCachedPage = getSharedQuestionList(redisKey);
            if (redisCachedPage != null) {
                cacheQuestionList(hotKey, redisCachedPage);
                return ResultUtils.success(redisCachedPage);
            }

            String lockKey = RedisConstant.getQuestionListLockKey(listCacheKey);
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = false;
            try {
                locked = lock.tryLock(QUESTION_LIST_LOCK_WAIT_SECONDS, QUESTION_LIST_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
                if (!locked) {
                    Page<QuestionVO> waitedQuestionList = waitForSharedQuestionList(redisKey, hotKey);
                    if (waitedQuestionList != null) {
                        return ResultUtils.success(waitedQuestionList);
                    }
                    log.warn("question list lock not acquired, degrade directly, cacheKey={}", listCacheKey);
                    return buildQuestionListDegradeResponse(questionQueryRequest, "题目列表正在加载中，请稍后重试");
                }

                redisCachedPage = getSharedQuestionList(redisKey);
                if (redisCachedPage != null) {
                    cacheQuestionList(hotKey, redisCachedPage);
                    return ResultUtils.success(redisCachedPage);
                }

                Page<QuestionVO> questionVOPage = loadQuestionVOPage(questionQueryRequest, request);
                cacheSharedQuestionList(redisKey, questionVOPage);
                cacheQuestionList(hotKey, questionVOPage);
                return ResultUtils.success(questionVOPage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "获取题目列表缓存锁失败");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Throwable ex) {
            // 业务异常
            if (!BlockException.isBlockException(ex)) {
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }
            return handleBlockException(questionQueryRequest, request, (BlockException) ex);
        } finally {
            if (entry != null) {
                entry.exit(1, remoteAddr);
            }
        }
    }

    /**
     * listQuestionVOByPageSentinel 流控 / 熔断处理：
     * 优先返回本地缓存兜底，避免热门列表在高峰期直接打挂用户体验。
     */
    public BaseResponse<Page<QuestionVO>> handleBlockException(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request,
                                                               BlockException ex) {
        BaseResponse<Page<QuestionVO>> redisFallback = getQuestionListRedisFallback(questionQueryRequest);
        if (redisFallback != null) {
            return redisFallback;
        }
        if (ex instanceof DegradeException) {
            return handleFallback(questionQueryRequest, request, ex);
        }
        return buildQuestionListDegradeResponse(questionQueryRequest, "访问过于频繁，请稍后再试");
    }

    /**
     * listQuestionVOByPageSentinel 降级操作：优先读取 Redis，共享缓存也没有时直接返回降级结果。
     */
    public BaseResponse<Page<QuestionVO>> handleFallback(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request, Throwable ex) {
        BaseResponse<Page<QuestionVO>> redisFallback = getQuestionListRedisFallback(questionQueryRequest);
        if (redisFallback != null) {
            return redisFallback;
        }
        return buildQuestionListDegradeResponse(questionQueryRequest, "题目列表降级中，请稍后再试");
    }

    private BaseResponse<Page<QuestionVO>> getQuestionListRedisFallback(QuestionQueryRequest questionQueryRequest) {
        String listCacheKey = buildQuestionListCacheKey(questionQueryRequest);
        String redisKey = RedisConstant.getQuestionListRedisKey(listCacheKey);
        Page<QuestionVO> redisCachedPage = getSharedQuestionList(redisKey);
        if (redisCachedPage == null) {
            return null;
        }
        String hotKey = buildQuestionFirstPageHotKey(questionQueryRequest);
        cacheQuestionList(hotKey, redisCachedPage);
        return ResultUtils.success(redisCachedPage);
    }

    private BaseResponse<Page<QuestionVO>> buildQuestionListDegradeResponse(QuestionQueryRequest questionQueryRequest,
                                                                            String message) {
        long current = questionQueryRequest == null || questionQueryRequest.getCurrent() <= 0 ? 1 : questionQueryRequest.getCurrent();
        long pageSize = questionQueryRequest == null || questionQueryRequest.getPageSize() <= 0 ? 10 : questionQueryRequest.getPageSize();
        Page<QuestionVO> degradePage = new Page<>(current, pageSize, 0);
        return new BaseResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), degradePage, message);
    }

    private String buildQuestionListCacheKey(QuestionQueryRequest questionQueryRequest) {
        if (questionQueryRequest == null) {
            return "question:list:default";
        }
        String source = String.format("current=%s|size=%s|search=%s|title=%s|content=%s|tags=%s|userId=%s|sortField=%s|sortOrder=%s|questionBankId=%s|difficulty=%s",
                questionQueryRequest.getCurrent(),
                questionQueryRequest.getPageSize(),
                questionQueryRequest.getSearchText(),
                questionQueryRequest.getTitle(),
                questionQueryRequest.getContent(),
                questionQueryRequest.getTags(),
                questionQueryRequest.getUserId(),
                questionQueryRequest.getSortField(),
                questionQueryRequest.getSortOrder(),
                questionQueryRequest.getQuestionBankId(),
                questionQueryRequest.getDifficulty());
        return "question:list:" + DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
    }

    private void clearQuestionListCaches() {
        for (String hotKey : questionFirstPageHotKeys) {
            removeHotKey(hotKey);
        }
        questionFirstPageHotKeys.clear();
        redissonClient.getKeys().deleteByPattern(RedisConstant.getQuestionListRedisPattern());
    }

    private Page<QuestionVO> loadQuestionVOPage(QuestionQueryRequest questionQueryRequest, HttpServletRequest request) {
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return questionService.getQuestionVOPage(questionPage, request);
    }

    private Page<QuestionVO> getSharedQuestionList(String redisKey) {
        RBucket<Page<QuestionVO>> bucket = redissonClient.getBucket(redisKey);
        return bucket.get();
    }

    private void cacheSharedQuestionList(String redisKey, Page<QuestionVO> questionVOPage) {
        RBucket<Page<QuestionVO>> bucket = redissonClient.getBucket(redisKey);
        bucket.set(questionVOPage, QUESTION_LIST_REDIS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void cacheQuestionList(String hotKey, Page<QuestionVO> questionVOPage) {
        if (hotKey != null) {
            questionFirstPageHotKeys.add(hotKey);
            smartSetHotKey(hotKey, questionVOPage);
        }
    }

    private Page<QuestionVO> waitForSharedQuestionList(String redisKey, String hotKey) {
        for (int i = 0; i < QUESTION_LIST_RETRY_TIMES; i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(QUESTION_LIST_RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            Page<QuestionVO> redisCachedPage = getSharedQuestionList(redisKey);
            if (redisCachedPage != null) {
                cacheQuestionList(hotKey, redisCachedPage);
                return redisCachedPage;
            }
        }
        return null;
    }

    protected String buildQuestionFirstPageHotKey(QuestionQueryRequest questionQueryRequest) {
        if (questionQueryRequest == null || questionQueryRequest.getQuestionBankId() == null) {
            return null;
        }
        int current = questionQueryRequest.getCurrent() <= 0 ? 1 : questionQueryRequest.getCurrent();
        if (current != 1) {
            return null;
        }
        int pageSize = questionQueryRequest.getPageSize() <= 0 ? 10 : questionQueryRequest.getPageSize();
        return String.format("question_first_page:bankId=%s|size=%s|sortField=%s|sortOrder=%s",
                questionQueryRequest.getQuestionBankId(),
                pageSize,
                questionQueryRequest.getSortField(),
                questionQueryRequest.getSortOrder());
    }

    protected boolean isHotKey(String key) {
        return JdHotKeyStore.isHotKey(key);
    }

    @SuppressWarnings("unchecked")
    protected Page<QuestionVO> getHotKeyQuestionListValue(String key) {
        Object cachedValue = JdHotKeyStore.get(key);
        if (cachedValue == null) {
            return null;
        }
        return (Page<QuestionVO>) cachedValue;
    }

    protected void smartSetHotKey(String key, Page<QuestionVO> questionVOPage) {
        JdHotKeyStore.smartSet(key, questionVOPage);
    }

    protected void removeHotKey(String key) {
        if (key == null) {
            return;
        }
        JdHotKeyStore.remove(key);
    }


    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        if (StrUtil.isBlank(question.getDifficulty())) {
            question.setDifficulty(QuestionDifficultyEnum.MEDIUM.getValue());
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        clearQuestionListCaches();
        return ResultUtils.success(true);
    }

    // endregion

    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        // 统一在 Controller 层做“主链路 + 降级链路”编排：
        // 1) 优先走 ES，获得更好的全文检索能力与查询性能
        // 2) ES 异常时自动降级到 MySQL，保证用户侧接口可用
        Page<Question> questionPage;
        try {
            // 优先走 ES 检索
            questionPage = questionService.searchFromEs(questionQueryRequest);
        } catch (Exception e) {
            // ES 不可用时自动降级到 MySQL，保证接口可用
            log.error("search from es failed, fallback to mysql", e);
            questionPage = questionService.listQuestionByPage(questionQueryRequest);
        }
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest) {
        ThrowUtils.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        clearQuestionListCaches();
        return ResultUtils.success(true);
    }

    /**
     * AI 生成题目（仅管理员可用）
     *
     * @param questionAIGenerateRequest 请求参数
     * @param request HTTP 请求
     * @return 是否生成成功
     */
    @PostMapping("/ai/generate/question")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> aiGenerateQuestions(@RequestBody QuestionAIGenerateRequest questionAIGenerateRequest, HttpServletRequest request) {
        String questionType = questionAIGenerateRequest.getQuestionType();
        int number = questionAIGenerateRequest.getNumber();
        // 校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题目类型不能为空");
        ThrowUtils.throwIf(number <= 0, ErrorCode.PARAMS_ERROR, "题目数量必须大于 0");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用 AI 生成题目服务
        questionService.aiGenerateQuestions(questionType, number, loginUser);
        clearQuestionListCaches();
        // 返回结果
        return ResultUtils.success(true);
    }
}
