package com.chen404.service.support.reader;

import com.chen404.config.ReaderImportProperties;
import com.chen404.config.ReaderImportTaskConfig;
import com.chen404.mapper.ReaderBookMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 持续补投已提交的导入任务；队列满或暂时故障时留待下轮，启动恢复同样走此入口。 */
@Slf4j
@Component
public class ReaderImportTaskRecovery {
    private final ReaderBookMapper bookMapper;
    private final ReaderImportTaskRunner taskRunner;
    private final ReaderImportProperties properties;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> polling;

    public ReaderImportTaskRecovery(ReaderBookMapper bookMapper, ReaderImportTaskRunner taskRunner,
                                    ReaderImportProperties properties,
                                    @Qualifier(ReaderImportTaskConfig.READER_IMPORT_LEASE_SCHEDULER)
                                    ScheduledExecutorService scheduler) {
        this.bookMapper = bookMapper;
        this.taskRunner = taskRunner;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startRecovery() {
        if (polling == null) {
            polling = scheduler.scheduleWithFixedDelay(this::recoverPendingImports, 0,
                    properties.getPollDelayMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** 单轮只读配置数量的 ID；任何失败都不能终止未来轮询。 */
    public void recoverPendingImports() {
        try {
            for (Long bookId : bookMapper.selectRecoverableImports(properties.getBatchSize())) {
                try {
                    taskRunner.runAsync(bookId);
                } catch (RejectedExecutionException exception) {
                    log.info("[READER_IMPORT_RECOVERY_DEFERRED] bookId={}", bookId);
                    return;
                }
            }
        } catch (RuntimeException exception) {
            log.error("[READER_IMPORT_RECOVERY_FAIL]", exception);
        }
    }

    @PreDestroy
    public synchronized void stopRecovery() {
        if (polling != null) {
            polling.cancel(false);
        }
    }
}
