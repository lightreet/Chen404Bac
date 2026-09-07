package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import com.chen404.exception.ApiException;
import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;
import com.chen404.util.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** 使用 Redis 原子 CAS 维护会话，支持多实例并发上传、关闭与幂等回填。 */
@Component
@RequiredArgsConstructor
public class TravelMobileUploadStore {
    private static final Duration RETENTION = Duration.ofMinutes(25);
    private static final int MAX_CAS_ATTEMPTS = 32;
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>();
    static {
        CAS.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/travel-mobile-cas.lua")));
        CAS.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public void create(TravelMobileUploadSession session) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(session.getId()), encode(session), RETENTION))) {
            throw new IllegalStateException("手机上传会话创建冲突");
        }
    }

    public TravelMobileUploadSession read(String id) {
        return decode(redis.opsForValue().get(key(id)));
    }

    /** 回调只修改内存对象，不得包含文件写入等外部副作用，因为冲突时会重试。 */
    public TravelMobileUploadSession update(String id, Consumer<TravelMobileUploadSession> change) {
        String key = key(id);
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            String original = redis.opsForValue().get(key);
            TravelMobileUploadSession session = decode(original);
            change.accept(session);
            if (Long.valueOf(1).equals(redis.execute(CAS, List.of(key), original, encode(session)))) {
                return session;
            }
        }
        throw new BadRequestException("上传状态正在更新，请稍后重试");
    }

    private String key(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new BadRequestException("上传链接无效，请重新扫码");
        }
        return RedisKeys.travelMobileSession(id);
    }

    private TravelMobileUploadSession decode(String value) {
        if (value == null) throw new ApiException(HttpStatus.GONE, ApiErrorCode.GONE, "上传链接已过期，请在电脑重新生成");
        try {
            return mapper.readValue(value, TravelMobileUploadSession.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("手机上传会话读取失败", e);
        }
    }

    private String encode(TravelMobileUploadSession value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("手机上传会话保存失败", e);
        }
    }
}
