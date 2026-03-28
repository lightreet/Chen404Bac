package com.chen404.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 工具类（统一序列化、TTL、常用操作）
 *
 * 约定：
 * - 只使用 StringRedisTemplate（key/value 统一 String），复杂对象使用 JSON。
 * - Key 统一走 {@link RedisKeys} 生成，避免散落的魔法字符串。
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public boolean exists(String key) {
        Boolean b = redis.hasKey(key);
        return Boolean.TRUE.equals(b);
    }

    public void delete(String key) {
        redis.delete(key);
    }

    public void expire(String key, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.expire(key, ttl);
    }

    // -------- String value --------

    public String getString(String key) {
        return redis.opsForValue().get(key);
    }

    public void setString(String key, String value, Duration ttl) {
        if (!StringUtils.hasText(key)) return;
        if (ttl == null) {
            redis.opsForValue().set(key, value);
        } else {
            redis.opsForValue().set(key, value, ttl);
        }
    }

    /**
     * 不存在时写入并设置 TTL；存在则返回 false（用于限流）
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        Boolean ok = ttl == null
                ? redis.opsForValue().setIfAbsent(key, value)
                : redis.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    public Long increment(String key) {
        return redis.opsForValue().increment(key);
    }

    // -------- JSON value --------

    public <T> T getJson(String key, Class<T> clazz) {
        String json = getString(key);
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public void setJson(String key, Object value, Duration ttl) {
        if (value == null) return;
        try {
            String json = objectMapper.writeValueAsString(value);
            setString(key, json, ttl);
        } catch (JsonProcessingException ignored) {
        }
    }
}

