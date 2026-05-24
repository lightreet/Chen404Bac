package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.config.SiteRuntimeProperties;
import com.chen404.config.UploadTaskConfig;
import com.chen404.domain.Result;
import com.chen404.domain.dto.MultiFileUploadDTO;
import com.chen404.domain.dto.SingleFileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.SysFileService;
import com.chen404.service.TravelMemoryImageMetadataService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 统一处理文章、头像、站点资源与受信申请附件上传。
 */
@Slf4j
@Tag(name = "文件上传", description = "图片上传、封面上传、头像上传等接口")
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private static final Map<String, String> IMAGE_EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp")
    );

    private static final Set<String> ALLOWED_TRUST_ATTACHMENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar"
    );

    private static final long MAX_IMAGE_SIZE = 12 * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 12 * 1024 * 1024;
    private static final long MAX_TRUST_ATTACHMENT_SIZE = 15 * 1024 * 1024;
    private static final int MAX_BATCH_IMAGE_COUNT = 10;

    private final SysFileService sysFileService;
    private final SiteRuntimeProperties siteRuntimeProperties;
    private final TravelMemoryImageMetadataService travelMemoryImageMetadataService;
    private final Executor uploadTaskExecutor;

    public UploadController(
            SysFileService sysFileService,
            SiteRuntimeProperties siteRuntimeProperties,
            TravelMemoryImageMetadataService travelMemoryImageMetadataService,
            @Qualifier(UploadTaskConfig.UPLOAD_TASK_EXECUTOR) Executor uploadTaskExecutor) {
        this.sysFileService = sysFileService;
        this.siteRuntimeProperties = siteRuntimeProperties;
        this.travelMemoryImageMetadataService = travelMemoryImageMetadataService;
        this.uploadTaskExecutor = uploadTaskExecutor;
    }

    @Operation(summary = "上传文章图片", description = "编辑器内单张图片上传")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadImage(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_CONTENT);
            log.info("用户 {} 上传文章图片成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("上传文章图片失败", e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @Operation(summary = "批量上传文章图片", description = "一次最多上传 10 张图片")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<UploadFileVO>> uploadImages(
            @ModelAttribute MultiFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile[] files = form.getFiles();

        if (files == null || files.length == 0) {
            return Result.error(400, "请选择要上传的文件");
        }
        if (files.length > MAX_BATCH_IMAGE_COUNT) {
            return Result.error(400, "一次最多上传 10 张图片");
        }
        long maxSize = resolveImageMaxSize();
        Set<String> allowedTypes = resolveAllowedImageTypes();

        List<CompletableFuture<UploadFileVO>> futures = Arrays.stream(files)
                .map(file -> CompletableFuture.supplyAsync(
                        () -> uploadSingleImage(file, userId, maxSize, allowedTypes),
                        uploadTaskExecutor))
                .toList();

        List<UploadFileVO> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            try {
                UploadFileVO result = futures.get(i).get();
                if (result.getUrl() != null) {
                    results.add(result);
                } else {
                    errors.add(files[i].getOriginalFilename() + ": " + result.getName());
                }
            } catch (Exception e) {
                log.error("批量上传等待结果失败，文件名: {}", files[i].getOriginalFilename(), e);
                errors.add(files[i].getOriginalFilename() + ": 上传失败");
            }
        }

        if (results.isEmpty()) {
            return Result.error(500, "所有文件上传失败: " + String.join(", ", errors));
        }

        String message = "上传成功 " + results.size() + " 张";
        if (!errors.isEmpty()) {
            message += "，失败 " + errors.size() + " 张";
        }
        log.info("用户 {} 批量上传文章图片成功: {} 张", userId, results.size());
        return Result.success(message, results);
    }

    @Operation(summary = "上传文章封面", description = "文章封面上传")
    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadCover(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_COVER);
            log.info("用户 {} 上传文章封面成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("上传文章封面失败", e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @RequireAdmin
    @Operation(summary = "上传站点资源图片", description = "站点配置中的 Logo、Favicon 与页面封面图片上传，保持原图不压缩")
    @PostMapping(value = "/site-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadSiteAsset(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.SITE_ASSET);
            log.info("用户 {} 上传站点资源成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("上传站点资源失败", e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @Operation(summary = "上传头像", description = "用户头像上传")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadAvatar(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.AVATAR);
            log.info("用户 {} 上传头像成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("上传头像失败", e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @Operation(summary = "上传受信申请附件", description = "用于受信任用户申请的附件上传")
    @PostMapping(value = "/trust-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadTrustAttachment(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateFile(
                file,
                MAX_TRUST_ATTACHMENT_SIZE,
                ALLOWED_TRUST_ATTACHMENT_TYPES,
                "仅允许上传图片、PDF、TXT、Word 或压缩包文件"
        );
        if (validateResult != null) {
            return validateResult;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.TRUST_REQUEST_ATTACHMENT);
            log.info("用户 {} 上传受信申请附件成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("上传受信申请附件失败", e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @RequireAdmin
    @Operation(summary = "上传旅行纪念图片", description = "仅管理员可用，上传后尝试解析 EXIF 经纬度与拍摄时间")
    @PostMapping(value = "/travel-memory-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadTravelMemoryImage(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        try {
            TravelMemoryImageMetadataService.TravelMemoryImageMetadata metadata =
                    travelMemoryImageMetadataService.extract(file);
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.TRAVEL_MEMORY_IMAGE);
            UploadFileVO data = buildUploadData(sysFile);
            data.setLatitude(metadata.latitude());
            data.setLongitude(metadata.longitude());
            data.setShotAt(metadata.shotAt());
            log.info("[TRAVEL_MEMORY_IMAGE_UPLOAD] userId={} url={} hasLatLng={} hasShotAt={}",
                    userId, sysFile.getFileUrl(), metadata.latitude() != null && metadata.longitude() != null,
                    metadata.shotAt() != null);
            return Result.success("上传成功", data);
        } catch (Exception e) {
            log.error("[TRAVEL_MEMORY_IMAGE_UPLOAD_FAIL] userId={} fileName={} message={}",
                    userId, file == null ? null : file.getOriginalFilename(), e.getMessage(), e);
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @Operation(summary = "删除文件", description = "根据文件 URL 删除已上传文件")
    @DeleteMapping("/file")
    public Result<Void> deleteFile(
            @Parameter(description = "文件 URL", required = true) @RequestParam("url") String url,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);

        try {
            boolean success = sysFileService.deleteByUrl(url, userId);
            if (success) {
                log.info("用户 {} 删除文件成功: {}", userId, url);
                return Result.success("删除成功");
            }
            return Result.error(500, "删除失败");
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return Result.error(500, "删除失败，请稍后重试");
        }
    }

    private UploadFileVO uploadSingleImage(MultipartFile file, Long userId, long maxSize, Set<String> allowedTypes) {
        Result<UploadFileVO> validateResult = validateImage(file, maxSize, allowedTypes);
        if (validateResult != null) {
            return buildErrorUploadResult(validateResult.getMessage());
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_CONTENT);
            return buildUploadData(sysFile);
        } catch (Exception e) {
            log.error("批量上传单文件失败，用户: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            return buildErrorUploadResult("上传失败");
        }
    }

    private Result<UploadFileVO> validateImage(MultipartFile file, long maxSize, Set<String> allowedTypes) {
        return validateFile(
                file,
                maxSize,
                allowedTypes,
                "仅允许上传图片文件（支持 jpg、png、gif、webp、bmp）"
        );
    }

    private long resolveImageMaxSize() {
        long value = siteRuntimeProperties.getUploadMaxSize();
        return value > 0 ? value : MAX_IMAGE_SIZE;
    }

    private Set<String> resolveAllowedImageTypes() {
        Set<String> resolved = new HashSet<>();
        for (String extension : siteRuntimeProperties.getUploadAllowTypes()) {
            String contentType = IMAGE_EXTENSION_TO_CONTENT_TYPE.get(Objects.toString(extension, "").trim().toLowerCase());
            if (contentType != null) {
                resolved.add(contentType);
            }
        }
        return resolved.isEmpty() ? ALLOWED_IMAGE_TYPES : resolved;
    }

    private Result<UploadFileVO> validateFile(
            MultipartFile file,
            long maxSize,
            Set<String> allowedContentTypes,
            String invalidTypeMessage) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        if (file.getSize() > maxSize) {
            return Result.error(400, "文件大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            return Result.error(400, invalidTypeMessage);
        }
        return null;
    }

    private UploadFileVO buildUploadData(SysFile sysFile) {
        UploadFileVO data = new UploadFileVO();
        data.setUrl(sysFile.getFileUrl());
        data.setName(sysFile.getFileName());
        data.setSize(String.valueOf(sysFile.getFileSize()));
        return data;
    }

    private UploadFileVO buildErrorUploadResult(String message) {
        UploadFileVO result = new UploadFileVO();
        result.setName(message);
        return result;
    }
}
