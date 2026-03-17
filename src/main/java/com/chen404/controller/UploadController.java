package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.SysFile;
import com.chen404.service.FileStorageService;
import com.chen404.service.SysFileService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 文件上传控制器
 */
@Slf4j
@Tag(name = "文件上传", description = "图片上传、封面上传、头像上传等相关接口")
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private SysFileService sysFileService;

    /**
     * 允许的图片类型
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    /**
     * 最大图片大小 12MB
     */
    private static final long MAX_IMAGE_SIZE = 12 * 1024 * 1024;

    /**
     * 最大封面大小 12MB
     */
    private static final long MAX_COVER_SIZE = 12 * 1024 * 1024;

    /**
     * 上传图片（用于编辑器内）
     */
    @Operation(summary = "上传图片", description = "编辑器内单张图片上传，用于文章内容")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @Parameter(description = "图片文件", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        // 校验文件
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
        if (validateResult != null) {
            return validateResult;
        }

        try {
            // 使用 SysFileService 上传临时文件
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_CONTENT);

            Map<String, String> data = new HashMap<>();
            data.put("url", sysFile.getFileUrl());
            data.put("name", sysFile.getFileName());
            data.put("size", String.valueOf(sysFile.getFileSize()));

            log.info("用户 {} 上传临时图片成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", data);

        } catch (Exception e) {
            log.error("上传图片失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传图片（并行处理优化版）
     */
    @Operation(summary = "批量上传图片", description = "一次最多上传10张图片，使用并行上传提高效率")
    @PostMapping("/images")
    public Result<List<Map<String, String>>> uploadImages(
            @Parameter(description = "图片文件列表", required = true) @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        if (files == null || files.length == 0) {
            return Result.error(400, "请选择要上传的文件");
        }

        if (files.length > 10) {
            return Result.error(400, "一次最多上传10张图片");
        }

        // 使用并行流提高上传效率
        List<CompletableFuture<Map<String, String>>> futures = Arrays.stream(files)
                .map(file -> CompletableFuture.supplyAsync(() -> uploadSingleImage(file)))
                .toList();

        // 等待所有上传完成
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
                errors.add(files[i].getOriginalFilename() + ": " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            return Result.error(500, "所有文件上传失败: " + String.join(", ", errors));
        }

        log.info("用户 {} 批量上传图片成功: {} 张", userId, results.size());
        return Result.success("上传成功 " + results.size() + " 张" + (errors.isEmpty() ? "" : ", 失败 " + errors.size() + " 张"), results);
    }

    /**
     * 上传单张图片（供批量上传使用）
     */
    private Map<String, String> uploadSingleImage(MultipartFile file) {
        Map<String, String> result = new HashMap<>();

        // 校验文件
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
        if (validateResult != null) {
            result.put("error", validateResult.getMessage());
            return result;
        }

        try {
            String objectName = generateObjectName("articles/content", file.getOriginalFilename());
            String fileUrl = fileStorageService.uploadFile(file, objectName);

            result.put("url", fileUrl);
            result.put("name", file.getOriginalFilename());
            result.put("size", String.valueOf(file.getSize()));

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 上传封面图
     */
    @Operation(summary = "上传封面图", description = "文章封面上传，建议尺寸 1200×630")
    @PostMapping("/cover")
    public Result<Map<String, String>> uploadCover(
            @Parameter(description = "封面图片", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        // 校验文件
        Result<Map<String, String>> validateResult = validateImage(file, MAX_COVER_SIZE);
        if (validateResult != null) {
            return validateResult;
        }

        try {
            // 使用 SysFileService 上传临时文件
            SysFile sysFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.ARTICLE_COVER);

            Map<String, String> data = new HashMap<>();
            data.put("url", sysFile.getFileUrl());
            data.put("name", sysFile.getFileName());
            data.put("size", String.valueOf(sysFile.getFileSize()));

            log.info("用户 {} 上传临时封面成功: {}", userId, sysFile.getFileUrl());
            return Result.success("上传成功", data);

        } catch (Exception e) {
            log.error("上传封面失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传头像
     */
    @Operation(summary = "上传头像", description = "用户头像上传")
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @Parameter(description = "头像图片", required = true) @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        // 校验文件
        Result<Map<String, String>> validateResult = validateImage(file, MAX_IMAGE_SIZE);
        if (validateResult != null) {
            return validateResult;
        }

        try {
            // 生成对象名称：avatars/2024/03/userId_uuid.jpg
            String extension = getFileExtension(file.getOriginalFilename());
            String objectName = String.format("avatars/%s/%d_%s%s",
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")),
                    userId,
                    UUID.randomUUID().toString().substring(0, 8),
                    extension);

            // 上传文件
            String fileUrl = fileStorageService.uploadFile(file, objectName);

            Map<String, String> data = new HashMap<>();
            data.put("url", fileUrl);
            data.put("name", file.getOriginalFilename());

            log.info("用户 {} 上传头像成功: {}", userId, fileUrl);
            return Result.success("上传成功", data);

        } catch (Exception e) {
            log.error("上传头像失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @Operation(summary = "删除文件", description = "根据文件URL删除已上传的文件")
    @DeleteMapping("/file")
    public Result<Void> deleteFile(
            @Parameter(description = "文件URL", required = true) @RequestParam("url") String url,
            HttpServletRequest request) {

        Long userId = RequestAttrUtil.requireUserId(request);

        try {
            // 使用 SysFileService 删除文件
            boolean success = sysFileService.deleteByUrl(url, userId);
            if (success) {
                log.info("用户 {} 删除文件成功: {}", userId, url);
                return Result.success("删除成功");
            } else {
                return Result.error(500, "删除失败");
            }

        } catch (Exception e) {
            log.error("删除文件失败", e);
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 校验图片文件
     */
    private Result<Map<String, String>> validateImage(MultipartFile file, long maxSize) {
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        if (file.getSize() > maxSize) {
            return Result.error(400, "文件大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return Result.error(400, "只允许上传图片文件 (jpg, png, gif, webp, bmp)");
        }

        return null;
    }

    /**
     * 生成对象名称
     */
    private String generateObjectName(String prefix, String originalName) {
        String extension = getFileExtension(originalName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return String.format("%s/%s/%s%s", prefix, datePath, uuid, extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 从URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            int bucketIndex = url.indexOf("/chen404/");
            if (bucketIndex != -1) {
                return url.substring(bucketIndex + "/chen404/".length());
            }

            java.net.URL urlObj = new java.net.URL(url);
            String path = urlObj.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.startsWith("chen404/")) {
                path = path.substring("chen404/".length());
            }
            return path;

        } catch (Exception e) {
            log.error("解析URL失败: {}", url);
            return null;
        }
    }
}