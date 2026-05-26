package com.chen404.job;

import com.chen404.service.FileReferenceService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class FileReferenceRebuildJob {

    private final FileReferenceService fileReferenceService;

    public FileReferenceRebuildJob(FileReferenceService fileReferenceService) {
        this.fileReferenceService = fileReferenceService;
    }

    @XxlJob("fileReferenceRebuildJobHandler")
    public void rebuildFileReferences() {
        log.info("Starting file reference rebuild job");
        Map<String, Integer> summary = fileReferenceService.rebuildAllReferences();
        log.info("File reference rebuild job finished, summary={}", summary);
    }
}
