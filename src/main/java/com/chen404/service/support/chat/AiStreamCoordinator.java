package com.chen404.service.support.chat;

import com.chen404.config.AiStreamProperties;
import com.chen404.exception.TooManyRequestsException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 在任何会话写入前执行准入；名额随工作线程退出释放，应用关闭时取消所有上游。 */
@Slf4j
@Component
public class AiStreamCoordinator {
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final AiStreamProperties properties;
    private final Map<AiStreamSession, Long> active = new HashMap<>();
    private boolean closing;

    public AiStreamCoordinator(@Qualifier("aiStreamExecutor") ThreadPoolExecutor executor,
            @Qualifier("aiStreamScheduler") ScheduledExecutorService scheduler, AiStreamProperties properties) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    public SseEmitter start(Long requesterId, Consumer<AiStreamSession> action) {
        AiStreamSession session = acquire(requesterId);
        try {
            session.setDeadline(scheduler.schedule(session::cancel, properties.getTotalTimeoutMs(), TimeUnit.MILLISECONDS));
            executor.execute(() -> run(session, action));
            return session.emitter();
        } catch (RejectedExecutionException ex) {
            session.cancel();
            release(session);
            log.warn("[AI_STREAM_REJECTED] requesterId={} reason=executor_unavailable", requesterId);
            throw new TooManyRequestsException("AI 正在忙，请稍后重试");
        }
    }

    private synchronized AiStreamSession acquire(Long requesterId) {
        long userCount = active.values().stream().filter(id -> java.util.Objects.equals(id, requesterId)).count();
        int userLimit = requesterId == null ? properties.getMaxAnonymous() : properties.getMaxPerUser();
        if (closing || active.size() >= properties.getMaxConcurrent() || userCount >= userLimit) {
            log.warn("[AI_STREAM_REJECTED] requesterId={} active={}", requesterId, active.size());
            throw new TooManyRequestsException("AI 并发请求过多，请稍后重试");
        }
        AiStreamSession session = new AiStreamSession(properties.getTotalTimeoutMs());
        active.put(session, requesterId);
        return session;
    }

    private void run(AiStreamSession session, Consumer<AiStreamSession> action) {
        try {
            if (session.begin()) {
                action.accept(session);
            }
        } catch (RuntimeException ex) {
            log.warn("[AI_STREAM_WORKER_FAIL] cancelled={}", session.isCancelled(), ex);
            session.cancel();
        } finally {
            release(session);
        }
    }

    private synchronized void release(AiStreamSession session) {
        session.finish();
        active.remove(session);
    }

    @PreDestroy
    public void shutdown() {
        AiStreamSession[] sessions;
        synchronized (this) {
            closing = true;
            sessions = active.keySet().toArray(AiStreamSession[]::new);
        }
        for (AiStreamSession session : sessions) {
            session.cancel();
        }
    }
}
