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
    private final TravelMemoryImageMetadataService metadataService;

    /** 直传与扫码上传共用；失败时已存储文件仍为临时文件，由清理任务回收。 */
    public UploadFileVO upload(MultipartFile file, Long userId) {
        return upload(file, userId, null);
    }

    /** 实况照片使用经过入口校验的原始照片作静态封面，保留 EXIF；普通视频截取首帧。 */
    public UploadFileVO upload(MultipartFile file, Long userId, MultipartFile originalPhoto) {
        if (!access.canCreateTravelMemory(userId)) {
            throw new ForbiddenException("没有旅行上传权限");
        }
        var media = processor.process(file);
        SysFile video = files.uploadTempFile(media.video(), userId, SysFile.RefType.TRAVEL_MEMORY_VIDEO);
        SysFile poster = files.uploadTempFile(originalPhoto == null ? media.poster() : originalPhoto,
                userId, SysFile.RefType.TRAVEL_MEMORY_IMAGE);
        UploadFileVO result = converter.fromVideo(poster, video);
        result.setName((originalPhoto == null ? file : originalPhoto).getOriginalFilename());
        if (originalPhoto != null) {
            var metadata = metadataService.extract(originalPhoto);
            result.setLatitude(metadata.latitude());
            result.setLongitude(metadata.longitude());
            result.setShotAt(metadata.shotAt());
        }
        log.info("[TRAVEL_VIDEO_UPLOAD] userId={} videoId={} posterId={} duration={} motionPhoto={}",
                userId, video.getId(), poster.getId(), media.durationSeconds(), originalPhoto != null);
        return result;
    }
}
