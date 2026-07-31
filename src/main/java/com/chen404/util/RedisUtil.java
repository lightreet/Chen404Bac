package com.chen404.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

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

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );
    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_CODE_SCRIPT = new DefaultRedisScript<>(
            "local stored = redis.call('GET', KEYS[1]); "
                    + "if not stored then return 0; end; "
                    + "if stored == ARGV[1] then "
                    + "redis.call('DEL', KEYS[1], KEYS[2]); return 1; "
                    + "end; "
                    + "local attempts = redis.call('INCR', KEYS[2]); "
                    + "if attempts == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[3]); end; "
                    + "if attempts >= tonumber(ARGV[2]) then "
                    + "redis.call('DEL', KEYS[1], KEYS[2]); return -2; "
                    + "end; "
                    + "return -1;",
            Long.class
    );

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

    /**
     * 原子自增，并仅在首次创建 key 时设置 TTL。
     */
    public Long incrementWithInitialTtl(String key, Duration ttl) {
        if (!StringUtils.hasText(key) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }
        return redis.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                String.valueOf(ttl.toMillis())
        );
    }

    /**
     * 原子校验并消费验证码。
     *
     * @return 1-验证成功，0-验证码不存在，-1-本次错误，-2-错误次数达到上限且验证码已锁定
     */
    public long verifyAndConsumeCode(
            String codeKey,
            String attemptsKey,
            String candidate,
            int maxAttempts,
            Duration attemptsTtl
    ) {
        Long result = redis.execute(
                VERIFY_AND_CONSUME_CODE_SCRIPT,
                List.of(codeKey, attemptsKey),
                candidate == null ? "" : candidate,
                String.valueOf(maxAttempts),
                String.valueOf(attemptsTtl.toMillis())
        );
        return result == null ? 0L : result;
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

