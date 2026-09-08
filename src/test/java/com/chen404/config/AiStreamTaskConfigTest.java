package com.chen404.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 Spring 自动选择的业务调度器不会占用流式请求的超时线程。 */
class AiStreamTaskConfigTest {
    @Test
    void blockingCleanupMustUseSeparateSchedulerAndAllowStreamDeadlineToRun() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(AiStreamProperties.class,
                AiStreamTaskConfig.class, FileDeletionTaskConfig.class, BlockingJob.class)) {
            BlockingJob job = context.getBean(BlockingJob.class);
            try {
                assertTrue(job.started.await(2, TimeUnit.SECONDS));
                assertTrue(job.threadName.get().startsWith("file-cleanup-"));
                CountDownLatch deadline = new CountDownLatch(1);
                AtomicReference<String> deadlineThread = new AtomicReference<>();
                context.getBean("aiStreamScheduler", ScheduledThreadPoolExecutor.class).schedule(() -> {
                    deadlineThread.set(Thread.currentThread().getName());
                    deadline.countDown();
                }, 10, TimeUnit.MILLISECONDS);
                assertTrue(deadline.await(1, TimeUnit.SECONDS));
                assertTrue(deadlineThread.get().startsWith("ai-stream-timeout-"));
            } finally {
                job.release.countDown();
            }
        }
    }

    static class BlockingJob {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<String> threadName = new AtomicReference<>();

        @Scheduled(fixedDelay = 10000)
        public void run() throws InterruptedException {
            threadName.set(Thread.currentThread().getName());
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
        }
    }
}
