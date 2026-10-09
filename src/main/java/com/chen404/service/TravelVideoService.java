package com.chen404.service;

import com.chen404.domain.dto.UploadFileVO;
import com.chen404.converter.TravelMobileUploadConverter;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.TravelVideoProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 旅行视频上传：鉴权、验证转码和封面存储，文件由旅行保存流程统一认领。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TravelVideoService {
    private final AccessService access;
    private final TravelVideoProcessor processor;
    private final SysFileService files;
    private final TravelMobileUploadConverter converter;

    /** 直传与扫码上传共用；失败时已存储文件仍为临时文件，由清理任务回收。 */
    public UploadFileVO upload(MultipartFile file, Long userId) {
        if (!access.canCreateTravelMemory(userId)) {
            throw new ForbiddenException("没有旅行上传权限");
        }
        var media = processor.process(file);
        SysFile video = files.uploadTempFile(media.video(), userId, SysFile.RefType.TRAVEL_MEMORY_VIDEO);
        SysFile poster = files.uploadTempFile(media.poster(), userId, SysFile.RefType.TRAVEL_MEMORY_IMAGE);
        UploadFileVO result = converter.fromVideo(poster, video);
        result.setName(file.getOriginalFilename());
        log.info("[TRAVEL_VIDEO_UPLOAD] userId={} videoId={} posterId={} duration={}",
                userId, video.getId(), poster.getId(), media.durationSeconds());
        return result;
    }
}
