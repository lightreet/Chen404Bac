package com.chen404.service.support.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次流式请求的取消作用域；断开、超时和发送失败均主动中止上游。 */
public final class AiStreamSession {
    private final SseEmitter emitter;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Thread worker;
    private Runnable upstreamCancellation;
    private ScheduledFuture<?> deadline;
    private boolean finished;

    AiStreamSession(long timeoutMs) {
        emitter = new SseEmitter(timeoutMs);
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(this::cancel);
        emitter.onError(error -> cancel());
    }

    public SseEmitter emitter() { return emitter; }

    public boolean isCancelled() { return cancelled.get(); }

    /** 注册与取消的竞争在同一锁内决定；已取消时立即执行注册动作。 */
    public AutoCloseable onCancellation(Runnable action) {
        synchronized (this) {
            if (!cancelled.get()) {
                upstreamCancellation = action;
                return () -> removeCancellation(action);
            }
        }
        action.run();
        return () -> { };
    }

    private synchronized void removeCancellation(Runnable action) {
        if (upstreamCancellation == action) {
            upstreamCancellation = null;
        }
    }

    /** 取消不会提前释放并发名额；必须等工作线程实际退出才能接受替代请求。 */
    public void cancel() {
        Runnable cancellation;
        synchronized (this) {
            if (finished || !cancelled.compareAndSet(false, true)) {
                return;
            }
            cancellation = upstreamCancellation;
            if (worker != null) {
                worker.interrupt();
            }
        }
        try {
            if (cancellation != null) {
                cancellation.run();
            }
        } finally {
            emitter.complete();
        }
    }

    synchronized void setDeadline(ScheduledFuture<?> scheduled) {
        deadline = scheduled;
        if (finished) {
            scheduled.cancel(false);
        }
    }

    synchronized boolean begin() {
        if (cancelled.get()) {
            return false;
        }
        worker = Thread.currentThread();
        return true;
    }

    synchronized void finish() {
        finished = true;
        worker = null;
        upstreamCancellation = null;
        if (deadline != null) {
            deadline.cancel(false);
        }
        emitter.complete();
    }
}
