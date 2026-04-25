package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.service.SysFileService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Tag(name = "文件上传", description = "图片上传、封面上传、头像上传等接口")
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
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

    @Autowired
    private SysFileService sysFileService;

    @Operation(summary = "上传文章图片", description = "编辑器内单张图片上传")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @Parameter(description = "图片文件", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
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
    @PostMapping("/images")
    public Result<List<Map<String, String>>> uploadImages(
            @Parameter(description = "图片文件列表", required = true) @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        if (files == null || files.length == 0) {
            return Result.error(400, "请选择要上传的文件");
        }
        if (files.length > 10) {
            return Result.error(400, "一次最多上传 10 张图片");
        }

        List<CompletableFuture<Map<String, String>>> futures = Arrays.stream(files)
                .map(file -> CompletableFuture.supplyAsync(() -> uploadSingleImage(file, userId)))
                .toList();

        List<Map<String, String>> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            try {
                Map<String, String> result = futures.get(i).get();
                if (result.containsKey("url")) {
                    results.add(result);
                } else {
                    errors.add(files[i].getOriginalFilename() + ": " + result.get("error"));
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
    @PostMapping("/cover")
    public Result<Map<String, String>> uploadCover(
            @Parameter(description = "封面图片", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);
        Result<Map<String, String>> validateResult = validateImage(file, MAX_COVER_SIZE);
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

    @Operation(summary = "上传头像", description = "用户头像上传")
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @Parameter(description = "头像图片", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
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
    @PostMapping("/trust-attachment")
    public Result<Map<String, String>> uploadTrustAttachment(
            @Parameter(description = "申请附件", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);
        Result<Map<String, String>> validateResult = validateFile(
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

    @Operation(summary = "删除文件", description = "根据文件 URL 删除已上传文件")
    @DeleteMapping("/file")
    public Result<Void> deleteFile(
            @Parameter(description = "文件 URL", required = true) @RequestParam("url") String url,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

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

    private Map<String, String> uploadSingleImage(MultipartFile file, Long userId) {
        Map<String, String> result = new HashMap<>();
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
        if (validateResult != null) {
            result.put("error", validateResult.getMessage());
            return result;
        }

        try {
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_CONTENT);
            result.putAll(buildUploadData(sysFile));
        } catch (Exception e) {
            log.error("批量上传单文件失败，用户: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            result.put("error", "上传失败");
        }
        return result;
    }

    private Result<Map<String, String>> validateImage(MultipartFile file, long maxSize) {
        return validateFile(
                file,
                maxSize,
                ALLOWED_IMAGE_TYPES,
                "仅允许上传图片文件（支持 jpg、png、gif、webp、bmp）"
        );
    }

    private Result<Map<String, String>> validateFile(
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

    private Map<String, String> buildUploadData(SysFile sysFile) {
        Map<String, String> data = new HashMap<>();
        data.put("url", sysFile.getFileUrl());
        data.put("name", sysFile.getFileName());
        data.put("size", String.valueOf(sysFile.getFileSize()));
        return data;
    }
}
