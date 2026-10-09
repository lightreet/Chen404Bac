package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.SingleFileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.TravelVideoService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 短视频使用独立入口，原有旅行图片上传契约保持兼容。 */
@RestController
@RequiredArgsConstructor
public class TravelVideoUploadController {
    private final TravelVideoService videos;

    @Operation(summary = "上传旅行短视频", description = "MP4/MOV/WebM，少于30秒且不超过60MB；返回封面url与videoUrl")
    @PostMapping(value = "/upload/travel-memory-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> upload(@ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("上传成功", videos.upload(form.getFile(), CurrentUserUtil.requireUserId(currentUser)));
    }
}
