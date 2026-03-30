package com.yupi.mianshiya.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.DeleteRequest;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.constant.RedisConstant;
import com.yupi.mianshiya.constant.UserConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.model.dto.questionBank.QuestionBankAddRequest;
import com.yupi.mianshiya.model.dto.questionBank.QuestionBankEditRequest;
import com.yupi.mianshiya.model.dto.questionBank.QuestionBankQueryRequest;
import com.yupi.mianshiya.model.dto.questionBank.QuestionBankUpdateRequest;
import com.yupi.mianshiya.model.entity.QuestionBank;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.QuestionBankVO;
import com.yupi.mianshiya.sentinel.SentinelConstant;
import com.yupi.mianshiya.service.QuestionBankService;
import com.yupi.mianshiya.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.DigestUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 题库控制器（题库维度的核心入口）。
 *
 * 主要职责：
 * 1) 提供题库 CRUD 与分页查询；
 * 2) 提供题库详情聚合（可按需携带题目分页）；
 * 3) 接入 Caffeine + HotKey + Sentinel，保障热点访问性能与可用性；
 * 4) 在更新 / 删除后主动失效本地缓存，减少脏读风险。
 *
 * 关键设计点：
 * - 详情页优先本地缓存，热点 key 命中时走 HotKey；
 * - Sentinel 触发限流 / 熔断后，优先回退到本地缓存数据；
 * - 控制器侧完成权限边界判定，复杂组装下沉至 Service。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    private static final long QUESTION_BANK_DETAIL_REDIS_TTL_SECONDS = 30 * 60L;

    private static final long QUESTION_BANK_DETAIL_LOCK_WAIT_SECONDS = 2L;

    private static final long QUESTION_BANK_DETAIL_LOCK_LEASE_SECONDS = 10L;

    private static final int QUESTION_BANK_DETAIL_RETRY_TIMES = 3;

    private static final long QUESTION_BANK_DETAIL_RETRY_MILLIS = 100L;

    /**
     * 题库详情本地缓存（配合 HotKey，兜底快速读取）
     */
    private final Cache<String, QuestionBankVO> questionBankDetailLocalCache = Caffeine.newBuilder()
            .initialCapacity(128)
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /**
     * 题库分页本地缓存（用于 Sentinel fallback 兜底）
     */
    private final Cache<String, Page<QuestionBankVO>> questionBankListLocalCache = Caffeine.newBuilder()
            .initialCapacity(64)
            .maximumSize(2_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * 记录当前节点已经写入过的题库详情 HotKey，便于题库更新时按题库维度主动失效分页变体。
     */
    private final Set<String> questionBankDetailHotKeys = ConcurrentHashMap.newKeySet();

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    // region 增删改查

    /**
     * 创建题库
     *
     * @param questionBankAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankAddRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        questionBank.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionBankService.save(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 题库变更后清理列表缓存
        clearQuestionBankListCache();
        // 返回新写入的数据 id
        long newQuestionBankId = questionBank.getId();
        return ResultUtils.success(newQuestionBankId);
    }

    /**
     * 删除题库
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestionBank.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        invalidateQuestionBankDetailCache(id);
        clearQuestionBankListCache();
        return ResultUtils.success(true);
    }

    /**
     * 更新题库（仅管理员可用）
     *
     * @param questionBankUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        if (questionBankUpdateRequest == null || questionBankUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankUpdateRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        // 判断是否存在
        long id = questionBankUpdateRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        invalidateQuestionBankDetailCache(questionBank.getId());
        clearQuestionBankListCache();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题库（封装类）
     *
     * @param questionBankQueryRequest
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQueryRequest.getId();
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        String detailCacheKey = buildQuestionBankDetailCacheKey(id);
        QuestionBankVO localCachedQuestionBankVO = questionBankDetailLocalCache.getIfPresent(detailCacheKey);
        if (localCachedQuestionBankVO != null) {
            return ResultUtils.success(localCachedQuestionBankVO);
        }

        String hotKey = buildQuestionBankHotKey(questionBankQueryRequest);
        if (hotKey != null && isHotKey(hotKey)) {
            QuestionBankVO hotCachedQuestionBankVO = getHotKeyValue(hotKey);
            if (hotCachedQuestionBankVO != null) {
                questionBankDetailLocalCache.put(detailCacheKey, hotCachedQuestionBankVO);
                return ResultUtils.success(hotCachedQuestionBankVO);
            }
        }

        String redisKey = RedisConstant.getQuestionBankDetailRedisKey(detailCacheKey);
        QuestionBankVO redisCachedQuestionBankVO = getSharedQuestionBankDetail(redisKey);
        if (redisCachedQuestionBankVO != null) {
            cacheQuestionBankDetail(detailCacheKey, hotKey, redisCachedQuestionBankVO);
            return ResultUtils.success(redisCachedQuestionBankVO);
        }

        String lockKey = RedisConstant.getQuestionBankDetailLockKey(detailCacheKey);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(QUESTION_BANK_DETAIL_LOCK_WAIT_SECONDS, QUESTION_BANK_DETAIL_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                QuestionBankVO waitedQuestionBankVO = waitForSharedQuestionBankDetail(redisKey, detailCacheKey, hotKey);
                if (waitedQuestionBankVO != null) {
                    return ResultUtils.success(waitedQuestionBankVO);
                }
                log.warn("question bank detail lock not acquired, fallback to direct load, id={}", id);
                QuestionBankVO fallbackQuestionBankVO = loadQuestionBankVO(id, request);
                cacheSharedQuestionBankDetail(redisKey, fallbackQuestionBankVO);
                cacheQuestionBankDetail(detailCacheKey, hotKey, fallbackQuestionBankVO);
                return ResultUtils.success(fallbackQuestionBankVO);
            }

            // double check，避免其他节点已填充共享缓存
            redisCachedQuestionBankVO = getSharedQuestionBankDetail(redisKey);
            if (redisCachedQuestionBankVO != null) {
                cacheQuestionBankDetail(detailCacheKey, hotKey, redisCachedQuestionBankVO);
                return ResultUtils.success(redisCachedQuestionBankVO);
            }

            QuestionBankVO loadedQuestionBankVO = loadQuestionBankVO(id, request);
            cacheSharedQuestionBankDetail(redisKey, loadedQuestionBankVO);
            cacheQuestionBankDetail(detailCacheKey, hotKey, loadedQuestionBankVO);
            return ResultUtils.success(loadedQuestionBankVO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取题库详情缓存锁失败");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 分页获取题库列表（仅管理员可用）
     *
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankPage);
    }

    /**
     * 分页获取题库列表（封装类）
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @SentinelResource(value = SentinelConstant.listQuestionBankVOByPage,
            blockHandler = "handleBlockException",
            fallback = "handleFallback")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        String listCacheKey = buildQuestionBankListCacheKey(questionBankQueryRequest);
        Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
        if (localCachePage != null) {
            return ResultUtils.success(localCachePage);
        }
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        Page<QuestionBankVO> questionBankVOPage = questionBankService.getQuestionBankVOPage(questionBankPage, request);
        questionBankListLocalCache.put(listCacheKey, questionBankVOPage);
        return ResultUtils.success(questionBankVOPage);
    }

    /**
     * listQuestionBankVOByPage 流控操作（此处为了方便演示，写在同一个类中）
     * 限流：提示“系统压力过大，请耐心等待”
     * 熔断：执行降级操作
     */
    public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                   HttpServletRequest request, BlockException ex) {
        // 降级操作
        if (ex instanceof DegradeException) {
            return handleFallback(questionBankQueryRequest, request, ex);
        }
        // 限流操作
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
    }

    /**
     * listQuestionBankVOByPage 降级操作：直接返回本地数据（此处为了方便演示，写在同一个类中）
     */
    public BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        // 熔断降级：优先返回本地缓存数据兜底
        String listCacheKey = buildQuestionBankListCacheKey(questionBankQueryRequest);
        Page<QuestionBankVO> localCachePage = questionBankListLocalCache.getIfPresent(listCacheKey);
        if (localCachePage != null) {
            return ResultUtils.success(localCachePage);
        }
        long current = questionBankQueryRequest == null ? 1 : questionBankQueryRequest.getCurrent();
        long pageSize = questionBankQueryRequest == null ? 10 : questionBankQueryRequest.getPageSize();
        return ResultUtils.success(new Page<>(current, pageSize, 0));
    }

    /**
     * 分页获取当前登录用户创建的题库列表
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionBankQueryRequest.setUserId(loginUser.getId());
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * 编辑题库（给用户使用）
     *
     * @param questionBankEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest, HttpServletRequest request) {
        if (questionBankEditRequest == null || questionBankEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankEditRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionBankEditRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestionBank.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        invalidateQuestionBankDetailCache(questionBank.getId());
        clearQuestionBankListCache();
        return ResultUtils.success(true);
    }

    // endregion

    private void invalidateQuestionBankDetailCache(Long questionBankId) {
        if (questionBankId == null) {
            return;
        }
        // 一次题库变更可能影响不同 detail 参数组合，直接全量失效更安全
        questionBankDetailLocalCache.invalidateAll();
        String hotKeyPrefix = buildQuestionBankHotKeyPrefix(questionBankId);
        for (String hotKey : questionBankDetailHotKeys) {
            if (hotKey.startsWith(hotKeyPrefix)) {
                removeHotKey(hotKey);
                questionBankDetailHotKeys.remove(hotKey);
            }
        }
        redissonClient.getKeys().deleteByPattern(RedisConstant.getQuestionBankDetailRedisPattern(questionBankId));
    }

    private void clearQuestionBankListCache() {
        questionBankListLocalCache.invalidateAll();
    }

    private String buildQuestionBankDetailCacheKey(Long questionBankId) {
        return String.format("question_bank:detail:id=%s", questionBankId);
    }

    private String buildQuestionBankListCacheKey(QuestionBankQueryRequest questionBankQueryRequest) {
        if (questionBankQueryRequest == null) {
            return "question_bank:list:default";
        }
        String source = String.format("current=%s|size=%s|search=%s|title=%s|desc=%s|picture=%s|userId=%s|sortField=%s|sortOrder=%s",
                questionBankQueryRequest.getCurrent(),
                questionBankQueryRequest.getPageSize(),
                questionBankQueryRequest.getSearchText(),
                questionBankQueryRequest.getTitle(),
                questionBankQueryRequest.getDescription(),
                questionBankQueryRequest.getPicture(),
                questionBankQueryRequest.getUserId(),
                questionBankQueryRequest.getSortField(),
                questionBankQueryRequest.getSortOrder());
        return "question_bank:list:" + DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
    }

    private QuestionBankVO loadQuestionBankVO(Long id, HttpServletRequest request) {
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        return questionBankService.getQuestionBankVO(questionBank, request);
    }

    private QuestionBankVO getSharedQuestionBankDetail(String redisKey) {
        RBucket<QuestionBankVO> bucket = redissonClient.getBucket(redisKey);
        return bucket.get();
    }

    private void cacheSharedQuestionBankDetail(String redisKey, QuestionBankVO questionBankVO) {
        RBucket<QuestionBankVO> bucket = redissonClient.getBucket(redisKey);
        bucket.set(questionBankVO, QUESTION_BANK_DETAIL_REDIS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void cacheQuestionBankDetail(String detailCacheKey, String hotKey, QuestionBankVO questionBankVO) {
        questionBankDetailLocalCache.put(detailCacheKey, questionBankVO);
        if (hotKey != null) {
            questionBankDetailHotKeys.add(hotKey);
            smartSetHotKey(hotKey, questionBankVO);
        }
    }

    private QuestionBankVO waitForSharedQuestionBankDetail(String redisKey, String detailCacheKey, String hotKey) {
        for (int i = 0; i < QUESTION_BANK_DETAIL_RETRY_TIMES; i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(QUESTION_BANK_DETAIL_RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            QuestionBankVO redisCachedQuestionBankVO = getSharedQuestionBankDetail(redisKey);
            if (redisCachedQuestionBankVO != null) {
                cacheQuestionBankDetail(detailCacheKey, hotKey, redisCachedQuestionBankVO);
                return redisCachedQuestionBankVO;
            }
        }
        return null;
    }

    protected String buildQuestionBankHotKey(QuestionBankQueryRequest questionBankQueryRequest) {
        if (questionBankQueryRequest == null || questionBankQueryRequest.getId() == null) {
            return null;
        }
        return String.format("bank_detail_id=%s", questionBankQueryRequest.getId());
    }

    protected String buildQuestionBankHotKeyPrefix(Long questionBankId) {
        if (questionBankId == null) {
            return null;
        }
        return "bank_detail_id=" + questionBankId + "|";
    }

    protected boolean isHotKey(String key) {
        return JdHotKeyStore.isHotKey(key);
    }

    @SuppressWarnings("unchecked")
    protected QuestionBankVO getHotKeyValue(String key) {
        Object cachedValue = JdHotKeyStore.get(key);
        if (cachedValue == null) {
            return null;
        }
        return (QuestionBankVO) cachedValue;
    }

    protected void smartSetHotKey(String key, QuestionBankVO questionBankVO) {
        JdHotKeyStore.smartSet(key, questionBankVO);
    }

    protected void removeHotKey(String key) {
        if (key == null) {
            return;
        }
        JdHotKeyStore.remove(key);
    }
}
