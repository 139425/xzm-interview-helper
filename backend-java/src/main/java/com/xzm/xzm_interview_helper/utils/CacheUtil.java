package com.xzm.xzm_interview_helper.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存工具类
 * 提供业务层面的缓存操作，基于RedisUtil封装
 */
@Slf4j
@Component
public class CacheUtil {

    @Autowired
    private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存键前缀常量
    public static final String USER_CACHE_PREFIX = "user:";
    public static final String CHAT_CACHE_PREFIX = "chat:";
    public static final String INTERVIEW_CACHE_PREFIX = "interview:";
    public static final String SESSION_CACHE_PREFIX = "session:";
    public static final String TEMP_CACHE_PREFIX = "temp:";

    // 默认过期时间（秒）
    public static final long DEFAULT_EXPIRE_TIME = 3600; // 1小时
    public static final long SHORT_EXPIRE_TIME = 300;    // 5分钟
    public static final long LONG_EXPIRE_TIME = 86400;   // 24小时

    /**
     * 缓存用户信息
     * @param userId 用户ID
     * @param userInfo 用户信息对象
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean cacheUserInfo(String userId, Object userInfo, long expireTime) {
        String key = USER_CACHE_PREFIX + userId;
        return redisUtil.set(key, userInfo, expireTime);
    }

    /**
     * 缓存用户信息（使用默认过期时间）
     * @param userId 用户ID
     * @param userInfo 用户信息对象
     * @return 是否成功
     */
    public boolean cacheUserInfo(String userId, Object userInfo) {
        return cacheUserInfo(userId, userInfo, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return 用户信息对象
     */
    public Object getUserInfo(String userId) {
        String key = USER_CACHE_PREFIX + userId;
        return redisUtil.get(key);
    }

    /**
     * 获取用户信息（指定类型）
     * @param userId 用户ID
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 用户信息对象
     */
    public <T> T getUserInfo(String userId, Class<T> clazz) {
        Object userInfo = getUserInfo(userId);
        if (userInfo == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(userInfo, clazz);
        } catch (Exception e) {
            log.error("转换用户信息类型失败, userId: {}, targetClass: {}", userId, clazz.getSimpleName(), e);
            return null;
        }
    }

    /**
     * 删除用户缓存
     * @param userId 用户ID
     */
    public void removeUserInfo(String userId) {
        String key = USER_CACHE_PREFIX + userId;
        redisUtil.del(key);
    }

    /**
     * 缓存聊天会话信息
     * @param sessionId 会话ID
     * @param sessionInfo 会话信息
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean cacheChatSession(String sessionId, Object sessionInfo, long expireTime) {
        String key = CHAT_CACHE_PREFIX + sessionId;
        return redisUtil.set(key, sessionInfo, expireTime);
    }

    /**
     * 获取聊天会话信息
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public Object getChatSession(String sessionId) {
        String key = CHAT_CACHE_PREFIX + sessionId;
        return redisUtil.get(key);
    }

    /**
     * 删除聊天会话缓存
     * @param sessionId 会话ID
     */
    public void removeChatSession(String sessionId) {
        String key = CHAT_CACHE_PREFIX + sessionId;
        redisUtil.del(key);
    }

    /**
     * 缓存面试信息
     * @param interviewId 面试ID
     * @param interviewInfo 面试信息
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean cacheInterviewInfo(String interviewId, Object interviewInfo, long expireTime) {
        String key = INTERVIEW_CACHE_PREFIX + interviewId;
        return redisUtil.set(key, interviewInfo, expireTime);
    }

    /**
     * 获取面试信息
     * @param interviewId 面试ID
     * @return 面试信息
     */
    public Object getInterviewInfo(String interviewId) {
        String key = INTERVIEW_CACHE_PREFIX + interviewId;
        return redisUtil.get(key);
    }

    /**
     * 删除面试信息缓存
     * @param interviewId 面试ID
     */
    public void removeInterviewInfo(String interviewId) {
        String key = INTERVIEW_CACHE_PREFIX + interviewId;
        redisUtil.del(key);
    }

    /**
     * 缓存临时数据
     * @param key 键
     * @param value 值
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean cacheTempData(String key, Object value, long expireTime) {
        String cacheKey = TEMP_CACHE_PREFIX + key;
        return redisUtil.set(cacheKey, value, expireTime);
    }

    /**
     * 缓存临时数据（使用短期过期时间）
     * @param key 键
     * @param value 值
     * @return 是否成功
     */
    public boolean cacheTempData(String key, Object value) {
        return cacheTempData(key, value, SHORT_EXPIRE_TIME);
    }

    /**
     * 获取临时数据
     * @param key 键
     * @return 值
     */
    public Object getTempData(String key) {
        String cacheKey = TEMP_CACHE_PREFIX + key;
        return redisUtil.get(cacheKey);
    }

    /**
     * 删除临时数据
     * @param key 键
     */
    public void removeTempData(String key) {
        String cacheKey = TEMP_CACHE_PREFIX + key;
        redisUtil.del(cacheKey);
    }

    /**
     * 缓存列表数据
     * @param key 键
     * @param list 列表数据
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean cacheList(String key, List<?> list, long expireTime) {
        return redisUtil.set(key, list, expireTime);
    }

    /**
     * 获取列表数据
     * @param key 键
     * @param typeReference 类型引用
     * @param <T> 泛型类型
     * @return 列表数据
     */
    public <T> List<T> getList(String key, TypeReference<List<T>> typeReference) {
        Object data = redisUtil.get(key);
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(data, typeReference);
        } catch (Exception e) {
            log.error("转换列表数据类型失败, key: {}", key, e);
            return null;
        }
    }

    /**
     * 增加计数器
     * @param key 键
     * @param delta 增量
     * @param expireTime 过期时间（秒）
     * @return 增加后的值
     */
    public long incrementCounter(String key, long delta, long expireTime) {
        long result = redisUtil.incr(key, delta);
        // 如果是第一次设置，需要设置过期时间
        if (result == delta) {
            redisUtil.expire(key, expireTime);
        }
        return result;
    }

    /**
     * 增加计数器（增量为1）
     * @param key 键
     * @param expireTime 过期时间（秒）
     * @return 增加后的值
     */
    public long incrementCounter(String key, long expireTime) {
        return incrementCounter(key, 1, expireTime);
    }

    /**
     * 获取计数器值
     * @param key 键
     * @return 计数器值
     */
    public long getCounter(String key) {
        Object value = redisUtil.get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.error("获取计数器值失败, key: {}", key, e);
            return 0;
        }
    }

    /**
     * 重置计数器
     * @param key 键
     */
    public void resetCounter(String key) {
        redisUtil.del(key);
    }

    /**
     * 检查缓存是否存在
     * @param key 键
     * @return 是否存在
     */
    public boolean exists(String key) {
        return redisUtil.hasKey(key);
    }

    /**
     * 设置缓存过期时间
     * @param key 键
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean expire(String key, long expireTime) {
        return redisUtil.expire(key, expireTime);
    }

    /**
     * 获取缓存剩余过期时间
     * @param key 键
     * @return 剩余时间（秒）
     */
    public long getExpire(String key) {
        return redisUtil.getExpire(key);
    }

    /**
     * 删除缓存
     * @param keys 键数组
     */
    public void delete(String... keys) {
        redisUtil.del(keys);
    }

    /**
     * 清除指定前缀的所有缓存
     * @param prefix 前缀
     * @return 清除的数量
     */
    public long clearCacheByPrefix(String prefix) {
        try {
            var keys = redisUtil.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisUtil.del(keys.toArray(new String[0]));
                log.info("清除缓存成功, prefix: {}, count: {}", prefix, keys.size());
                return keys.size();
            }
            return 0;
        } catch (Exception e) {
            log.error("清除缓存失败, prefix: {}", prefix, e);
            return 0;
        }
    }

    /**
     * 清除用户相关的所有缓存
     * @param userId 用户ID
     * @return 清除的数量
     */
    public long clearUserCache(String userId) {
        return clearCacheByPrefix(USER_CACHE_PREFIX + userId);
    }

    /**
     * 清除临时缓存
     * @return 清除的数量
     */
    public long clearTempCache() {
        return clearCacheByPrefix(TEMP_CACHE_PREFIX);
    }
}