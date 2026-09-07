package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.TravelMobileUploadDTO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.TravelMobileUploadService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 桌面需要账号鉴权，手机端只接受独立上传凭证，所有响应禁止缓存。 */
@RestController
@RequestMapping("/upload/travel-mobile")
@RequiredArgsConstructor
@Tag(name = "旅行手机直传")
public class TravelMobileUploadController {
    private final TravelMobileUploadService service;

    @ModelAttribute
    public void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @PostMapping("/sessions")
    @Operation(summary = "创作者发起手机上传")
    public Result<TravelMobileUploadDTO.Created> create(@Valid @RequestBody TravelMobileUploadDTO.Create request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.create(CurrentUserUtil.requireUserId(user), request));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "所有者读取并续租桌面上传会话")
    public Result<TravelMobileUploadDTO.Snapshot> poll(@PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.poll(id, CurrentUserUtil.requireUserId(user)));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "所有者结束上传，保存前可要求无上传中的文件")
    public Result<TravelMobileUploadDTO.Snapshot> close(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean requireIdle, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.close(id, CurrentUserUtil.requireUserId(user), requireIdle));
    }

    @GetMapping("/public/{id}")
    @Operation(summary = "手机凭证读取目标和回执，无需账号登录")
    public Result<TravelMobileUploadDTO.Snapshot> status(@PathVariable String id,
            @RequestHeader(value = "X-Upload-Token", required = false) String token,
            @RequestParam(required = false) String batchId) {
        return Result.success(service.mobileStatus(id, token, batchId));
    }

    @PostMapping("/public/{id}/batches/{batchId}")
    @Operation(summary = "手机凭证开始一批照片上传，期间阻止桌面提前保存")
    public Result<Void> beginBatch(@PathVariable String id, @PathVariable String batchId,
            @RequestHeader(value = "X-Upload-Token", required = false) String token) {
        service.beginBatch(id, token, batchId);
        return Result.success();
    }

    @DeleteMapping("/public/{id}/batches/{batchId}")
    @Operation(summary = "手机凭证结束当前上传批次")
    public Result<Void> endBatch(@PathVariable String id, @PathVariable String batchId,
            @RequestHeader(value = "X-Upload-Token", required = false) String token) {
        service.endBatch(id, token, batchId);
        return Result.success();
    }

    @PostMapping(value = "/public/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "手机凭证上传单张照片，多张由客户端队列逐张发送")
    public Result<TravelMobileUploadDTO.Receipt> upload(@PathVariable String id,
            @RequestHeader(value = "X-Upload-Token", required = false) String token,
            @RequestParam(required = false) String requestId, @RequestParam(required = false) MultipartFile file) {
        return Result.success(service.upload(id, token, requestId, file));
    }
}
