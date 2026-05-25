package com.chen404.job;

import com.chen404.service.SysFileService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 文件清理定时任务
 * 定期清理过期的临时文件和未使用的文件资源
 */
@Slf4j
@Component
public class FileCleanupJob {

    @Autowired
    private SysFileService sysFileService;

    /**
     * 由 XXL-JOB 调度执行，清理过期的临时文件。
     */
    @XxlJob("fileCleanupJobHandler")
    public void cleanExpiredTempFiles() {
        log.info("开始执行过期临时文件清理任务...");
        int count = sysFileService.cleanExpiredTempFiles();
        log.info("过期临时文件清理任务完成，共清理 {} 个文件", count);
    }
}
