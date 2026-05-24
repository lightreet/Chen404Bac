package com.chen404.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 上传任务线程池配置，避免批量上传占用 ForkJoin 公共线程池。
 */
@Configuration
public class UploadTaskConfig {

    public static final String UPLOAD_TASK_EXECUTOR = "uploadTaskExecutor";

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 50;
    private static final int AWAIT_TERMINATION_SECONDS = 30;

    @Bean(name = UPLOAD_TASK_EXECUTOR)
    public Executor uploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("upload-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }
}
