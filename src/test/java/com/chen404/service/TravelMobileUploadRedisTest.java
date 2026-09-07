package com.chen404.service;

import com.chen404.service.support.TravelMobileUploadSession;
import com.chen404.service.support.TravelMobileUploadStore;
import com.chen404.util.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 可选真实 Redis 集成验证，端口必须显式指向隔离实例，不读取应用配置。 */
@EnabledIfEnvironmentVariable(named = "TRAVEL_MOBILE_TEST_REDIS_PORT", matches = "\\d+")
class TravelMobileUploadRedisTest {
    @Test void concurrentUpdatesKeepEveryMutationAndOriginalExpiry() throws Exception {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("TRAVEL_MOBILE_TEST_REDIS_PORT")));
        factory.afterPropertiesSet();
        var redis = new StringRedisTemplate(factory);
        var store = new TravelMobileUploadStore(redis, new ObjectMapper().findAndRegisterModules());
        var session = new TravelMobileUploadSession();
        session.setId(UUID.randomUUID().toString());
        var item = new TravelMobileUploadSession.Item();
        item.setStatus(com.chen404.domain.enums.TravelMobilePhotoStatus.UPLOADING);
        session.getItems().put("test-photo-request", item);
        store.create(session);
        String key = RedisKeys.travelMobileSession(session.getId());
        redis.expire(key, java.time.Duration.ofSeconds(30));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++) tasks.add(executor.submit(() -> store.update(session.getId(), s -> s.setMaxCount(s.getMaxCount() + 1))));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(20, store.read(session.getId()).getMaxCount());
            assertEquals(com.chen404.domain.enums.TravelMobilePhotoStatus.UPLOADING,
                    store.read(session.getId()).getItems().get("test-photo-request").getStatus());
            assertTrue(redis.getExpire(key) <= 30 && redis.getExpire(key) > 0);
            redis.delete(key);
            var gone = assertThrows(com.chen404.exception.ApiException.class, () -> store.read(session.getId()));
            assertEquals(410, gone.getCode());
        } finally { executor.shutdownNow(); redis.delete(key); factory.destroy(); }
    }
}
