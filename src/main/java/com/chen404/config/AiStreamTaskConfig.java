package com.chen404.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Spring 管理工作线程和超时检查线程；所有队列和工作线程都有资源上限。 */
@Configuration
public class AiStreamTaskConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor aiStreamExecutor(AiStreamProperties properties) {
        int workers = properties.getMaxConcurrent();
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers), new CustomizableThreadFactory("ai-stream-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledThreadPoolExecutor aiStreamScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2,
                new CustomizableThreadFactory("ai-stream-timeout-"));
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }
}
