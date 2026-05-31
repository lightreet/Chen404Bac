package com.chen404.job;

import com.chen404.service.FileReferenceService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 音乐曲目文件引用同步任务。
 * 用于重复补齐历史 music_track -> file_reference 数据，不影响其他模块的引用记录。
 */
@Slf4j
@Component
public class MusicTrackFileReferenceSyncJob {

    private final FileReferenceService fileReferenceService;

    public MusicTrackFileReferenceSyncJob(FileReferenceService fileReferenceService) {
        this.fileReferenceService = fileReferenceService;
    }

    /**
     * 由 XXL-JOB 调度执行，重建全部音乐曲目的文件引用。
     */
    @XxlJob("musicTrackFileReferenceSyncJobHandler")
    public void syncMusicTrackFileReferences() {
        log.info("[MUSIC_FILE_REFERENCE_SYNC_JOB] start");
        Map<String, Integer> summary = fileReferenceService.rebuildMusicTrackReferences();
        log.info("[MUSIC_FILE_REFERENCE_SYNC_JOB] done summary={}", summary);
    }
}
