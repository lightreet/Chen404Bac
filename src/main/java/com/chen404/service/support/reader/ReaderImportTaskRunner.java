package com.chen404.service.support.reader;

import com.chen404.config.ReaderImportTaskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 有界执行器入口；同实例避免重复排队，跨实例由处理器的数据库租约保护。 */
@Slf4j
@Service
public class ReaderImportTaskRunner {
    private final ReaderBookImportProcessor processor;
    private final Executor executor;
    private final Set<Long> scheduledBooks = ConcurrentHashMap.newKeySet();

    public ReaderImportTaskRunner(ReaderBookImportProcessor processor,
                                 @Qualifier(ReaderImportTaskConfig.READER_IMPORT_TASK_EXECUTOR) Executor executor) {
        this.processor = processor;
        this.executor = executor;
    }

    /** 拒绝提交时释放本地登记并传播拒绝，持久化任务保持可恢复状态。 */
    public void runAsync(Long bookId) {
        if (!scheduledBooks.add(bookId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    processor.process(bookId);
                } catch (RuntimeException exception) {
                    // 认领或故障记录自身失败时保留租约，过期后恢复器重试。
                    log.error("[READER_IMPORT_EXECUTION_FAIL] bookId={}", bookId, exception);
                } finally {
                    scheduledBooks.remove(bookId);
                }
            });
        } catch (RejectedExecutionException exception) {
            scheduledBooks.remove(bookId);
            throw exception;
        }
    }
}
