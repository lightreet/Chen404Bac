package com.chen404.job;

import com.chen404.service.FileDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期处理已提交的文件删除任务；多实例通过持久化租约协调。 */
@Component
@RequiredArgsConstructor
public class FileDeletionJob {
    private final FileDeletionService deletionService;

    @Scheduled(initialDelayString = "${file.cleanup.initial-delay-ms:30000}",
            fixedDelayString = "${file.cleanup.delay-ms:30000}")
    public void processPending() {
        deletionService.processPending();
    }
}
