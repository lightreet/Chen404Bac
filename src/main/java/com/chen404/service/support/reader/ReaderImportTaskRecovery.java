package com.chen404.service.support.reader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.mapper.ReaderBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务启动后恢复已经保存原文件、但尚未完成解析的书架导入任务。
 */
@Slf4j
@Component
public class ReaderImportTaskRecovery {

    private final ReaderBookMapper bookMapper;
    private final ReaderImportTaskRunner taskRunner;

    public ReaderImportTaskRecovery(ReaderBookMapper bookMapper, ReaderImportTaskRunner taskRunner) {
        this.bookMapper = bookMapper;
        this.taskRunner = taskRunner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingImports() {
        List<ReaderBook> pendingBooks = bookMapper.selectList(new LambdaQueryWrapper<ReaderBook>()
                .eq(ReaderBook::getStatus, ReaderBook.STATUS_IMPORTING)
                .orderByAsc(ReaderBook::getCreateTime)
                .orderByAsc(ReaderBook::getId));
        if (pendingBooks.isEmpty()) {
            return;
        }
        log.info("[READER_IMPORT_RECOVERY] pendingCount={}", pendingBooks.size());
        for (ReaderBook book : pendingBooks) {
            try {
                taskRunner.runAsync(book.getId());
            } catch (TaskRejectedException exception) {
                log.error("[READER_IMPORT_RECOVERY_REJECTED] bookId={}", book.getId(), exception);
                break;
            }
        }
    }
}
