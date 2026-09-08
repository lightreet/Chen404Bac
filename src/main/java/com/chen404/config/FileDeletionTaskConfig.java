package com.chen404.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 本地持久化清理队列不依赖可选的 XXL-JOB 服务。 */
@Configuration
@EnableScheduling
public class FileDeletionTaskConfig {
    /** 阻塞的对象清理不能占用 AI 超时调度线程。 */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("file-cleanup-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
