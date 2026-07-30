package com.chen404.service.support.reader;

import com.chen404.config.ReaderImportTaskConfig;
import com.chen404.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 小说后台导入任务入口，负责隔离异步线程异常并落下可轮询的失败状态。
 */
@Slf4j
@Service
public class ReaderImportTaskRunner {

    private final ReaderBookImportProcessor processor;

    public ReaderImportTaskRunner(ReaderBookImportProcessor processor) {
        this.processor = processor;
    }

    @Async(ReaderImportTaskConfig.READER_IMPORT_TASK_EXECUTOR)
    public void runAsync(Long bookId) {
        try {
            processor.process(bookId);
        } catch (Exception exception) {
            String userMessage = userFailureMessage(exception);
            log.error("[READER_IMPORT_FAIL] bookId={} message={}", bookId, userMessage, exception);
            try {
                processor.markFailed(bookId, userMessage);
            } catch (Exception stateException) {
                log.error("[READER_IMPORT_FAIL_STATE_ERROR] bookId={}", bookId, stateException);
            }
        }
    }

    private String userFailureMessage(Exception exception) {
        if (exception instanceof BadRequestException && StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return "后台导入失败，请删除后重新导入";
    }
}
