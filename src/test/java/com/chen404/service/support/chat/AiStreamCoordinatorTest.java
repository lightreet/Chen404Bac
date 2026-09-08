package com.chen404.service.support.chat;

import com.chen404.config.AiStreamProperties;
import com.chen404.config.AiStreamTaskConfig;
import com.chen404.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class AiStreamCoordinatorTest {
    private final AiStreamProperties limits = new AiStreamProperties();
    private final CountDownLatch releaseWorkers = new CountDownLatch(1);
    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor scheduler;
    private AiStreamCoordinator coordinator;

    @BeforeEach
    void setUp() {
        limits.setMaxConcurrent(2);
        limits.setMaxPerUser(1);
        limits.setMaxAnonymous(1);
        AiStreamTaskConfig config = new AiStreamTaskConfig();
        executor = config.aiStreamExecutor(limits);
        scheduler = config.aiStreamScheduler();
        coordinator = new AiStreamCoordinator(executor, scheduler, limits);
    }

    @AfterEach
    void stop() throws Exception {
        releaseWorkers.countDown();
        coordinator.shutdown();
        executor.shutdownNow();
        scheduler.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void shouldEnforceUserAnonymousAndGlobalQuotasBeforeStartingWork() {
        coordinator.start(7L, session -> holdWorker());
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(7L, session -> fail("must not run")));
        coordinator.start(null, session -> holdWorker());
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(null, session -> fail("must not run")));
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(8L, session -> fail("must not run")));
        assertTrue(executor.getPoolSize() <= 2);
        assertTrue(executor.getQueue().size() <= 2);
    }

    @Test
    void timeoutMustNotReleaseQuotaUntilWorkerReallyStops() {
        limits.setTotalTimeoutMs(120);
        AtomicReference<AiStreamSession> active = new AtomicReference<>();
        var emitter = coordinator.start(7L, session -> {
            active.set(session);
            holdWorker();
        });
        assertEquals(120L, emitter.getTimeout());
        await().atMost(2, TimeUnit.SECONDS).until(() -> active.get() != null && active.get().isCancelled());
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(7L, session -> fail("worker still running")));
        releaseWorkers.countDown();
        await().atMost(2, TimeUnit.SECONDS).until(() -> executor.getActiveCount() == 0);
        CountDownLatch replacement = new CountDownLatch(1);
        coordinator.start(7L, session -> replacement.countDown());
        await().atMost(2, TimeUnit.SECONDS).until(() -> replacement.getCount() == 0);
    }

    @Test
    void workerFailureAndExecutorRejectionMustNotLeakReservationsOrTimers() {
        coordinator.start(7L, session -> { throw new IllegalStateException("test failure"); });
        await().atMost(2, TimeUnit.SECONDS).until(() -> executor.getCompletedTaskCount() == 1);
        CountDownLatch replacement = new CountDownLatch(1);
        coordinator.start(7L, session -> replacement.countDown());
        await().atMost(2, TimeUnit.SECONDS).until(() -> replacement.getCount() == 0 && executor.getActiveCount() == 0);
        assertEquals(0, scheduler.getQueue().size());
        executor.shutdown();
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(7L, session -> fail("must not run")));
        assertEquals(0, scheduler.getQueue().size());
    }

    @Test
    void shutdownMustCancelRegisteredUpstreamAndRejectFurtherWork() {
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch registered = new CountDownLatch(1);
        coordinator.start(7L, session -> {
            session.onCancellation(cancelled::countDown);
            registered.countDown();
            holdWorker();
        });
        await().atMost(2, TimeUnit.SECONDS).until(() -> registered.getCount() == 0);
        coordinator.shutdown();
        assertEquals(0, cancelled.getCount());
        assertThrows(TooManyRequestsException.class, () -> coordinator.start(8L, session -> fail("must not run")));
    }

    /** 故意不立即响应中断，验证取消标志不能被当成资源已经释放。 */
    private void holdWorker() {
        while (releaseWorkers.getCount() > 0) {
            try {
                if (releaseWorkers.await(2, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException ignored) {
                // 模拟需要额外时间才能退出的上游调用。
            }
        }
    }
}
