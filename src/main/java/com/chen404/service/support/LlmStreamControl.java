package com.chen404.service.support;

import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 独立检查阻塞读取的截止时间，取消 HTTP Future 并关闭响应流以唤醒读取线程。 */
@Slf4j
final class LlmStreamControl implements AutoCloseable {
    private static final long CHECK_INTERVAL_MS = 50;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicLong lastActivity = new AtomicLong(System.nanoTime());
    private final long deadline;
    private final long idleNanos;
    private final LlmTextStreamHandler handler;
    private final ScheduledFuture<?> watchdog;
    private final AutoCloseable cancellationRegistration;
    private Future<?> request;
    private InputStream body;
    private volatile boolean timedOut;

    LlmStreamControl(LlmTextStreamHandler handler, ScheduledExecutorService scheduler,
            long totalTimeoutMs, long idleTimeoutMs) {
        this.handler = handler;
        this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        this.idleNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeoutMs);
        this.cancellationRegistration = handler.onCancellation(this::cancel);
        this.watchdog = scheduler.scheduleAtFixedRate(this::check, CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    void track(CompletableFuture<HttpResponse<InputStream>> future) {
        synchronized (this) {
            request = future;
            if (cancelled.get()) {
                future.cancel(true);
            }
        }
        // Future 已完成但等待线程恰好被中断时，也必须接管并关闭其响应体。
        future.thenAccept(response -> {
            try {
                track(response.body());
            } catch (IOException ex) {
                log.debug("[LLM_STREAM_CANCEL_BEFORE_BODY]", ex);
            }
        });
    }

    synchronized InputStream track(InputStream input) throws IOException {
        body = input;
        if (cancelled.get()) {
            input.close();
            throw new IOException("Stream cancelled before body read");
        }
        lastActivity.set(System.nanoTime());
        return new FilterInputStream(input) {
            @Override
            public int read() throws IOException {
                int value = in.read();
                if (value >= 0) {
                    lastActivity.set(System.nanoTime());
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = in.read(buffer, offset, length);
                if (count > 0) {
                    lastActivity.set(System.nanoTime());
                }
                return count;
            }
        };
    }

    boolean timedOut() { return timedOut; }

    private void check() {
        long now = System.nanoTime();
        if (handler.isCancelled()) {
            cancel();
        } else if (now >= deadline || now - lastActivity.get() >= idleNanos) {
            timedOut = true;
            cancel();
        }
    }

    private void cancel() {
        Future<?> pending;
        InputStream input;
        synchronized (this) {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            pending = request;
            input = body;
        }
        if (pending != null) {
            pending.cancel(true);
        }
        if (input != null) {
            try {
                input.close();
            } catch (IOException ex) {
                log.debug("[LLM_STREAM_CANCEL_CLOSE_FAIL]", ex);
            }
        }
    }

    @Override
    public void close() {
        watchdog.cancel(false);
        cancel();
        try {
            cancellationRegistration.close();
        } catch (Exception ex) {
            log.debug("[LLM_STREAM_CANCEL_UNREGISTER_FAIL]", ex);
        }
    }
}
