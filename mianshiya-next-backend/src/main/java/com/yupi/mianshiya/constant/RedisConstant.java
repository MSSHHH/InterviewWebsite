package com.yupi.mianshiya.constant;

/**
 * Redis 常量
 */
public interface RedisConstant {

    /**
     * 用户签到记录的 Redis key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * 题库详情共享缓存 Redis key 前缀
     */
    String QUESTION_BANK_DETAIL_REDIS_KEY_PREFIX = "question_bank:detail:shared";

    /**
     * 题库详情查询锁 key 前缀
     */
    String QUESTION_BANK_DETAIL_LOCK_KEY_PREFIX = "question_bank:detail:lock";

    /**
     * 题目列表共享缓存 Redis key 前缀
     */
    String QUESTION_LIST_REDIS_KEY_PREFIX = "question:list:shared";

    /**
     * 题目列表查询锁 key 前缀
     */
    String QUESTION_LIST_LOCK_KEY_PREFIX = "question:list:lock";

    /**
     * 获取用户签到记录的 Redis Key
     * @param year 年份
     * @param userId 用户 id
     * @return 拼接好的 Redis Key
     */
    static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }

    /**
     * 获取题库详情共享缓存 key
     *
     * @param detailCacheKey 详情缓存 key
     * @return Redis key
     */
    static String getQuestionBankDetailRedisKey(String detailCacheKey) {
        return String.format("%s:%s", QUESTION_BANK_DETAIL_REDIS_KEY_PREFIX, detailCacheKey);
    }

    /**
     * 获取题库详情锁 key
     *
     * @param detailCacheKey 详情缓存 key
     * @return lock key
     */
    static String getQuestionBankDetailLockKey(String detailCacheKey) {
        return String.format("%s:%s", QUESTION_BANK_DETAIL_LOCK_KEY_PREFIX, detailCacheKey);
    }

    /**
     * 获取按题库 id 删除详情共享缓存时的 pattern
     *
     * @param questionBankId 题库 id
     * @return Redis pattern
     */
    static String getQuestionBankDetailRedisPattern(long questionBankId) {
        return String.format("%s:question_bank:detail:id=%s|*", QUESTION_BANK_DETAIL_REDIS_KEY_PREFIX, questionBankId);
    }

    /**
     * 获取题目列表共享缓存 key
     *
     * @param listCacheKey 列表缓存 key
     * @return Redis key
     */
    static String getQuestionListRedisKey(String listCacheKey) {
        return String.format("%s:%s", QUESTION_LIST_REDIS_KEY_PREFIX, listCacheKey);
    }

    /**
     * 获取题目列表锁 key
     *
     * @param listCacheKey 列表缓存 key
     * @return lock key
     */
    static String getQuestionListLockKey(String listCacheKey) {
        return String.format("%s:%s", QUESTION_LIST_LOCK_KEY_PREFIX, listCacheKey);
    }

    /**
     * 获取题目列表共享缓存全量失效 pattern
     *
     * @return Redis pattern
     */
    static String getQuestionListRedisPattern() {
        return String.format("%s:*", QUESTION_LIST_REDIS_KEY_PREFIX);
    }
}
