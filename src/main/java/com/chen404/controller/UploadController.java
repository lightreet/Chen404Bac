package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.config.SiteRuntimeProperties;
import com.chen404.config.UploadTaskConfig;
import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import com.chen404.domain.dto.MultiFileUploadDTO;
import com.chen404.domain.dto.SingleFileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.SysFileService;
import com.chen404.service.AccessService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 统一处理文章、头像、站点资源与好友申请附件上传。
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
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/wave",
            "audio/x-wav",
            "audio/vnd.wave",
            "audio/flac",
            "audio/ogg",
            "audio/aac",
            "audio/mp4",
            "audio/x-m4a"
    );
    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of(
            "mp3",
            "wav",
            "flac",
            "ogg",
            "aac",
            "m4a"
    );

    private static final long MAX_IMAGE_SIZE = 12 * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 12 * 1024 * 1024;
    private static final long MAX_AUDIO_SIZE = 60 * 1024 * 1024;
    private static final long MAX_TRUST_ATTACHMENT_SIZE = 15 * 1024 * 1024;
    private static final int MAX_BATCH_IMAGE_COUNT = 10;

    private static final String MSG_UPLOAD_SUCCESS = "上传成功";
    private static final String MSG_UPLOAD_RETRY = "上传失败，请稍后重试";
    private static final String MSG_FILE_EMPTY = "文件不能为空";

    private final SysFileService sysFileService;
    private final SiteRuntimeProperties siteRuntimeProperties;
    private final TravelMemoryImageMetadataService travelMemoryImageMetadataService;
    private final Executor uploadTaskExecutor;
    private final AccessService accessService;

    public UploadController(
            SysFileService sysFileService,
            SiteRuntimeProperties siteRuntimeProperties,
            TravelMemoryImageMetadataService travelMemoryImageMetadataService,
            AccessService accessService,
            @Qualifier(UploadTaskConfig.UPLOAD_TASK_EXECUTOR) Executor uploadTaskExecutor) {
        this.sysFileService = sysFileService;
        this.siteRuntimeProperties = siteRuntimeProperties;
        this.travelMemoryImageMetadataService = travelMemoryImageMetadataService;
        this.accessService = accessService;
        this.uploadTaskExecutor = uploadTaskExecutor;
    }

    @Operation(summary = "上传文章图片", description = "编辑器内单张图片上传")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadImage(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureArticleCreator(userId);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        return executeUpload(file, userId, SysFile.RefType.ARTICLE_CONTENT, "ARTICLE_IMAGE_UPLOAD");
    }

    @Operation(summary = "批量上传文章图片", description = "一次最多上传 " + MAX_BATCH_IMAGE_COUNT + " 张图片")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<UploadFileVO>> uploadImages(
            @ModelAttribute MultiFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureArticleCreator(userId);
        MultipartFile[] files = form.getFiles();

        if (files == null || files.length == 0) {
            return Result.error(ApiErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (files.length > MAX_BATCH_IMAGE_COUNT) {
            return Result.error(ApiErrorCode.BAD_REQUEST, "一次最多上传 " + MAX_BATCH_IMAGE_COUNT + " 张图片");
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
            return Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, "所有文件上传失败: " + String.join(", ", errors));
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
        ensureArticleCreator(userId);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, resolveImageMaxSize(), resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        return executeUpload(file, userId, SysFile.RefType.ARTICLE_COVER, "ARTICLE_COVER_UPLOAD");
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

        return executeUpload(file, userId, SysFile.RefType.SITE_ASSET, "SITE_ASSET_UPLOAD");
    }

    @Operation(summary = "上传音乐音频", description = "知友或管理员上传音乐馆歌曲音频")
    @PostMapping(value = "/music-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadMusicAudio(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureMusicCreator(userId);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateMusicAudioFile(file);
        if (validateResult != null) {
            return validateResult;
        }

        return executeUpload(file, userId, SysFile.RefType.MUSIC_AUDIO, "MUSIC_AUDIO_UPLOAD");
    }

    @Operation(summary = "上传音乐封面", description = "知友或管理员上传音乐馆歌曲封面")
    @PostMapping(value = "/music-cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadMusicCover(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureMusicCreator(userId);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, MAX_COVER_SIZE, resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }

        return executeUpload(file, userId, SysFile.RefType.MUSIC_COVER, "MUSIC_COVER_UPLOAD");
    }

    @Operation(summary = "上传小说封面", description = "登录用户上传书架小说的自定义封面")
    @PostMapping(value = "/novel-cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadNovelCover(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        MultipartFile file = form.getFile();
        Result<UploadFileVO> validateResult = validateImage(file, MAX_COVER_SIZE, resolveAllowedImageTypes());
        if (validateResult != null) {
            return validateResult;
        }
        return executeUpload(file, userId, SysFile.RefType.NOVEL_COVER, "NOVEL_COVER_UPLOAD");
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

        return executeUpload(file, userId, SysFile.RefType.AVATAR, "AVATAR_UPLOAD");
    }

    @Operation(summary = "上传好友申请附件", description = "用于好友申请的附件上传")
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

        return executeUpload(file, userId, SysFile.RefType.TRUST_REQUEST_ATTACHMENT, "TRUST_ATTACHMENT_UPLOAD");
    }

    @Operation(summary = "上传旅行纪念图片", description = "知友或管理员可用，上传后尝试解析 EXIF 经纬度与拍摄时间")
    @PostMapping(value = "/travel-memory-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadFileVO> uploadTravelMemoryImage(
            @ModelAttribute SingleFileUploadDTO form,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureTravelCreator(userId);
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
            return Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, MSG_UPLOAD_RETRY);
        }
    }

    private void ensureArticleCreator(Long userId) {
        if (!accessService.canCreateArticle(userId)) {
            throw new ForbiddenException("仅知友或管理员可上传文章资源");
        }
    }

    private void ensureTravelCreator(Long userId) {
        if (!accessService.canCreateTravelMemory(userId)) {
            throw new ForbiddenException("仅知友或管理员可上传旅行图片");
        }
    }

    private void ensureMusicCreator(Long userId) {
        if (!accessService.canCreateMusicTrack(userId)) {
            throw new ForbiddenException("仅知友或管理员可上传音乐资源");
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
            return Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, "删除失败");
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, "删除失败，请稍后重试");
        }
    }

    /**
     * 统一处理"临时文件上传 + 事件日志 + 响应组装"的公共流程。
     * 调用前必须已完成文件校验；日志使用稳定事件码，成功为 [label_OK]，失败为 [label_FAIL]。
     */
    private Result<UploadFileVO> executeUpload(
            MultipartFile file,
            Long userId,
            String refType,
            String eventLabel) {
        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, refType);
            log.info("[{}_OK] userId={} url={}", eventLabel, userId, sysFile.getFileUrl());
            return Result.success(MSG_UPLOAD_SUCCESS, buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("[{}_FAIL] userId={} fileName={}", eventLabel, userId, file.getOriginalFilename(), e);
            return Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, MSG_UPLOAD_RETRY);
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
        Result<UploadFileVO> basicResult = validateFileBasics(file, maxSize);
        if (basicResult != null) {
            return basicResult;
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            return Result.error(ApiErrorCode.BAD_REQUEST, invalidTypeMessage);
        }
        return null;
    }

    /**
     * 音频上传既兼容标准 MIME，也兼容部分浏览器只带扩展名或上报变体 MIME 的情况。
     */
    private Result<UploadFileVO> validateMusicAudioFile(MultipartFile file) {
        Result<UploadFileVO> basicResult = validateFileBasics(file, MAX_AUDIO_SIZE);
        if (basicResult != null) {
            return basicResult;
        }
        if (hasAllowedContentType(file, ALLOWED_AUDIO_TYPES) || hasAllowedExtension(file, ALLOWED_AUDIO_EXTENSIONS)) {
            return null;
        }
        return Result.error(ApiErrorCode.BAD_REQUEST, "仅允许上传 mp3、wav、flac、ogg、aac 或 m4a 音频文件");
    }

    /**
     * 空文件与大小上限是所有上传共享的基础校验；通过时返回 null。
     */
    private Result<UploadFileVO> validateFileBasics(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            return Result.error(ApiErrorCode.BAD_REQUEST, MSG_FILE_EMPTY);
        }
        if (file.getSize() > maxSize) {
            return Result.error(ApiErrorCode.BAD_REQUEST, "文件大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        return null;
    }

    private boolean hasAllowedContentType(MultipartFile file, Set<String> allowedContentTypes) {
        String contentType = file.getContentType();
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private boolean hasAllowedExtension(MultipartFile file, Set<String> allowedExtensions) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }
        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == originalFilename.length() - 1) {
            return false;
        }
        String extension = originalFilename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        return allowedExtensions.contains(extension);
    }

    private UploadFileVO buildUploadData(SysFile sysFile) {
        UploadFileVO data = new UploadFileVO();
        data.setId(sysFile.getId());
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
