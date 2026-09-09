package com.chen404.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 小说解析任务线程池。
 *
 * <p>解析任务可能同时消耗 CPU、内存和数据库写入资源，因此与普通上传任务隔离并限制并发；
 * 队列只保存书籍 ID，原始文件由受保护对象存储持久化，避免大文件内容长期占用堆内存。</p>
 */
@Configuration
public class ReaderImportTaskConfig {

    public static final String READER_IMPORT_TASK_EXECUTOR = "readerImportTaskExecutor";
    public static final String READER_IMPORT_LEASE_SCHEDULER = "readerImportLeaseScheduler";

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 100;
    private static final int AWAIT_TERMINATION_SECONDS = 60;

    @Bean(name = READER_IMPORT_TASK_EXECUTOR)
    public Executor readerImportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("reader-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    /** 恢复扫描及续租与文件存储清理隔离，避免阻塞清理延迟任务恢复。 */
    @Bean(name = READER_IMPORT_LEASE_SCHEDULER, destroyMethod = "shutdownNow")
    public ScheduledExecutorService readerImportLeaseScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, action -> {
            Thread thread = new Thread(action, "reader-import-lease");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
