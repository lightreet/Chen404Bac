package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.config.MinioConfig;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.SysFileMapper;
import com.chen404.service.AccessService;
import com.chen404.service.FileClaim;
import com.chen404.service.FileStorageService;
import com.chen404.service.ImageProcessingService;
import com.chen404.service.ManagedFileUrlCodec;
import com.chen404.service.ProcessedImage;
import com.chen404.service.SysFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 系统文件服务实现
 */
@Slf4j
@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private ImageProcessingService imageProcessingService;

    @Autowired
    private ManagedFileUrlCodec managedFileUrlCodec;

    @Autowired
    private MinioConfig minioConfig;

    // 临时文件过期时间（小时）
    private static final int TEMP_FILE_EXPIRE_HOURS = 24;

    // 匹配 Markdown 图片 ![alt](url) 和 HTML 图片 <img src="url">
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "!\\[(.*?)\\]\\((.*?)\\)|<img[^>]+src=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<SysFile> listByIds(Collection<? extends Serializable> idList) {
        if (idList == null || idList.isEmpty()) {
            return List.of();
        }

        List<Serializable> validIds = idList.stream()
                .filter(id -> id != null && StringUtils.hasText(String.valueOf(id)))
                .distinct()
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            return List.of();
        }

        return super.listByIds(validIds);
    }

    @Override
    @Transactional
    public SysFile uploadTempFile(MultipartFile file, Long userId, String refType) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String prefix = refType.toLowerCase().replace("article_", "");

        Optional<ProcessedImage> processed = imageProcessingService.process(file, refType);

        final String objectName;
        final String fileUrl;
        final long fileSize;
        final String contentType;
        final String storedFileName;
        boolean protectedStorage = isProtectedRefType(refType);
        String bucketName = protectedStorage
                ? minioConfig.getProtectedBucketName()
                : minioConfig.getBucketName();

        if (processed.isPresent()) {
            ProcessedImage p = processed.get();
            byte[] payload = p.bytes();
            storedFileName = replaceExtension(originalFilename, p.extension());
            objectName = generateObjectName(prefix, storedFileName);
            fileUrl = fileStorageService.uploadFile(
                    new ByteArrayInputStream(payload),
                    bucketName,
                    objectName,
                    p.contentType(),
                    payload.length
            );
            fileSize = (long) payload.length;
            contentType = p.contentType();
        } else {
            storedFileName = originalFilename;
            objectName = generateObjectName(prefix, originalFilename);
            fileUrl = fileStorageService.uploadFile(file, bucketName, objectName);
            fileSize = file.getSize();
            contentType = file.getContentType();
        }

        SysFile sysFile = new SysFile();
        sysFile.setFileName(storedFileName);
        sysFile.setFileOriginalName(originalFilename);
        sysFile.setObjectName(objectName);
        sysFile.setStorageScope(protectedStorage
                ? SysFile.StorageScope.PROTECTED
                : SysFile.StorageScope.PUBLIC);
        sysFile.setBucketName(bucketName);
        sysFile.setFilePath(objectName);
        sysFile.setFileUrl(fileUrl);
        sysFile.setFileSize(fileSize);
        sysFile.setContentType(contentType);
        sysFile.setUserId(userId);
        sysFile.setStatus(SysFile.Status.TEMP);
        sysFile.setRefType(refType);
        sysFile.setExpireTime(LocalDateTime.now().plusHours(TEMP_FILE_EXPIRE_HOURS));

        save(sysFile);
        if (protectedStorage) {
            sysFile.setFileUrl(managedFileUrlCodec.stableUrl(sysFile.getId()));
            updateById(sysFile);
            sysFile.setFileUrl(managedFileUrlCodec.ticketedUrl(sysFile.getId()));
        }
        log.info("用户 {} 上传临时文件成功: {}", userId, fileUrl);

        return sysFile;
    }

    @Override
    @Transactional
    public void claimPermanentFiles(
            Long operatorId,
            List<FileClaim> claims,
            String expectedRefType,
            Long refId) {
        if (operatorId == null || refId == null || !StringUtils.hasText(expectedRefType)) {
            throw new BadRequestException("文件认领参数不完整");
        }
        if (claims == null || claims.isEmpty()) {
            return;
        }

        Map<Long, SysFile> filesById = new LinkedHashMap<>();
        for (FileClaim claim : claims) {
            SysFile file = resolveClaimedFile(claim);
            if (file != null) {
                filesById.putIfAbsent(file.getId(), file);
            }
        }

        for (SysFile file : filesById.values()) {
            validateFileClaim(file, operatorId, expectedRefType, refId);
            if (isClaimedBySameBusiness(file, expectedRefType, refId)) {
                continue;
            }
            file.setStatus(SysFile.Status.PERMANENT);
            file.setRefType(expectedRefType);
            file.setRefId(refId);
            file.setExpireTime(null);
            updateById(file);
        }

        log.info("[FILE_CLAIM_OK] operatorId={} refType={} refId={} fileCount={}",
                operatorId,
                expectedRefType,
                refId,
                filesById.size());
    }

    @Override
    @Transactional
    public boolean deleteByUrl(String fileUrl, Long userId) {
        SysFile file = resolveByManagedUrlOrStoredUrl(fileUrl);
        if (file == null) {
            log.warn("删除文件失败，文件记录不存在: {}", fileUrl);
            return false;
        }

        if (!accessService.canDeleteFile(userId, file)) {
            throw new ForbiddenException("仅文件上传者本人或管理员可删除该文件");
        }

        // 删除存储中的文件
        boolean deleted = fileStorageService.deleteFile(resolveBucketName(file), file.getObjectName());
        if (deleted) {
            // 标记为已删除
            file.setStatus(SysFile.Status.DELETED);
            updateById(file);
            log.info("用户 {} 删除文件成功: {}", userId, fileUrl);
        }

        return deleted;
    }

    @Override
    @Transactional
    public int cleanExpiredTempFiles() {
        List<SysFile> expiredFiles = baseMapper.selectExpiredTempFiles();
        int count = 0;

        for (SysFile file : expiredFiles) {
            try {
                // 删除存储中的文件
                boolean deleted = fileStorageService.deleteFile(resolveBucketName(file), file.getObjectName());
                if (deleted) {
                    // 逻辑删除记录
                    removeById(file.getId());
                    count++;
                    log.info("清理过期临时文件成功: {}", file.getFileUrl());
                }
            } catch (Exception e) {
                log.error("清理过期临时文件失败: {}", file.getFileUrl(), e);
            }
        }

        if (count > 0) {
            log.info("本次共清理 {} 个过期临时文件", count);
        }

        return count;
    }

    @Override
    public List<String> extractImageUrlsFromContent(String content) {
        List<String> urls = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return urls;
        }

        Matcher matcher = IMAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            // Markdown 格式: group(2)
            String markdownUrl = matcher.group(2);
            // HTML 格式: group(3)
            String htmlUrl = matcher.group(3);

            if (markdownUrl != null && !markdownUrl.isEmpty()) {
                urls.add(markdownUrl.trim());
            } else if (htmlUrl != null && !htmlUrl.isEmpty()) {
                urls.add(htmlUrl.trim());
            }
        }

        return urls.stream().distinct().collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int cleanUnusedFiles(Long articleId, String newContent, String newCoverUrl) {
        // 获取文章当前关联的所有文件
        List<SysFile> existingFiles = baseMapper.selectFilesByArticleId(articleId);
        if (existingFiles.isEmpty()) {
            return 0;
        }

        // 提取新内容中的所有图片URL
        List<String> usedUrls = extractImageUrlsFromContent(newContent);

        // 添加封面URL（如果有）
        if (newCoverUrl != null && !newCoverUrl.isEmpty()) {
            usedUrls.add(newCoverUrl);
        }

        // 找出不再使用的文件
        List<SysFile> unusedFiles = existingFiles.stream()
                .filter(file -> !usedUrls.contains(file.getFileUrl()))
                .collect(Collectors.toList());

        int count = 0;
        for (SysFile file : unusedFiles) {
            try {
                // 删除存储中的文件
                boolean deleted = fileStorageService.deleteFile(resolveBucketName(file), file.getObjectName());
                if (deleted) {
                    // 逻辑删除记录
                    removeById(file.getId());
                    count++;
                    log.info("清理文章 {} 的未使用文件成功: {}", articleId, file.getFileUrl());
                }
            } catch (Exception e) {
                log.error("清理文章 {} 的未使用文件失败: {}", articleId, file.getFileUrl(), e);
            }
        }

        if (count > 0) {
            log.info("文章 {} 共清理 {} 个未使用文件", articleId, count);
        }

        return count;
    }

    @Override
    public String extractObjectNameFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        try {
            // 从 URL 中提取对象名称
            // 格式: http://host:port/bucketName/objectName
            int bucketIndex = fileUrl.indexOf("/chen404/");
            if (bucketIndex != -1) {
                return fileUrl.substring(bucketIndex + "/chen404/".length());
            }

            java.net.URL url = new java.net.URL(fileUrl);
            String path = url.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.startsWith("chen404/")) {
                path = path.substring("chen404/".length());
            }
            return path;
        } catch (Exception e) {
            log.error("解析URL失败: {}", fileUrl);
            return null;
        }
    }

    @Override
    public Long findAvatarFileIdForUser(Long userId, String avatarUrl) {
        if (userId == null || !StringUtils.hasText(avatarUrl)) {
            return null;
        }
        SysFile file = resolveByManagedUrlOrStoredUrl(avatarUrl);
        if (file == null || !userId.equals(file.getUserId())) {
            return null;
        }
        if (!SysFile.RefType.AVATAR.equals(file.getRefType())) {
            return null;
        }
        return file.getId();
    }

    @Override
    public SysFile findByFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        return resolveByManagedUrlOrStoredUrl(fileUrl);
    }

    @Override
    public Long findCoverFileIdForArticle(Long articleId, String coverImageUrl) {
        if (articleId == null || !StringUtils.hasText(coverImageUrl)) {
            return null;
        }
        SysFile file = resolveByManagedUrlOrStoredUrl(coverImageUrl);
        if (file == null || file.getId() == null) {
            return null;
        }
        if (!articleId.equals(file.getRefId())) {
            return null;
        }
        if (!SysFile.RefType.ARTICLE_COVER.equals(file.getRefType())) {
            return null;
        }
        return file.getId();
    }

    /**
     * URL-only claims are retained for article and travel compatibility. Unknown URLs are treated as
     * external resources; managed file URLs always resolve to a sys_file row and enter strict checks.
     */
    private SysFile resolveClaimedFile(FileClaim claim) {
        if (claim == null) {
            return null;
        }
        SysFile fileById = claim.fileId() == null ? null : getById(claim.fileId());
        SysFile fileByUrl = resolveByManagedUrlOrStoredUrl(claim.fileUrl());

        if (claim.fileId() != null && fileById == null) {
            throw new BadRequestException("文件不存在或已删除");
        }
        if (fileById != null
                && StringUtils.hasText(claim.fileUrl())
                && (fileByUrl == null || !Objects.equals(fileById.getId(), fileByUrl.getId()))) {
            throw new BadRequestException("文件 ID 与 URL 不匹配");
        }
        return fileById != null ? fileById : fileByUrl;
    }

    private void validateFileClaim(
            SysFile file,
            Long operatorId,
            String expectedRefType,
            Long refId) {
        if (file.getId() == null || SysFile.Status.DELETED.equals(file.getStatus())) {
            throw new BadRequestException("文件不存在或已删除");
        }
        if (!Objects.equals(file.getRefType(), expectedRefType)) {
            throw new BadRequestException("文件类型与目标业务不匹配");
        }
        if (isClaimedBySameBusiness(file, expectedRefType, refId)) {
            return;
        }
        if (!SysFile.Status.TEMP.equals(file.getStatus())) {
            throw new BadRequestException("文件已被其他业务占用");
        }
        if (file.getExpireTime() != null && !file.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("临时文件已过期，请重新上传");
        }
        if (!Objects.equals(file.getUserId(), operatorId)) {
            log.warn("[FILE_CLAIM_DENIED] operatorId={} fileId={} ownerId={} refType={} refId={}",
                    operatorId,
                    file.getId(),
                    file.getUserId(),
                    expectedRefType,
                    refId);
            throw new ForbiddenException("只能使用自己上传的文件");
        }
        if (file.getRefId() != null) {
            throw new BadRequestException("文件已被其他业务占用");
        }
    }

    private boolean isClaimedBySameBusiness(SysFile file, String refType, Long refId) {
        return SysFile.Status.PERMANENT.equals(file.getStatus())
                && Objects.equals(file.getRefType(), refType)
                && Objects.equals(file.getRefId(), refId);
    }

    private SysFile resolveByManagedUrlOrStoredUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        Long managedFileId = managedFileUrlCodec.resolveFileId(fileUrl);
        return managedFileId == null
                ? baseMapper.selectByUrl(fileUrl.trim())
                : getById(managedFileId);
    }

    private String resolveBucketName(SysFile file) {
        return StringUtils.hasText(file.getBucketName())
                ? file.getBucketName()
                : minioConfig.getBucketName();
    }

    private boolean isProtectedRefType(String refType) {
        return SysFile.RefType.ARTICLE_CONTENT.equals(refType)
                || SysFile.RefType.ARTICLE_COVER.equals(refType)
                || SysFile.RefType.TRAVEL_MEMORY_IMAGE.equals(refType)
                || SysFile.RefType.MUSIC_AUDIO.equals(refType)
                || SysFile.RefType.MUSIC_COVER.equals(refType)
                || SysFile.RefType.TRUST_REQUEST_ATTACHMENT.equals(refType);
    }

    private static String replaceExtension(String originalName, String newExtension) {
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return "image" + newExtension;
        }
        return originalName.substring(0, originalName.lastIndexOf('.')) + newExtension;
    }

    /**
     * 生成对象名称
     */
    private String generateObjectName(String prefix, String originalName) {
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String datePath = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM"));
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return String.format("%s/%s/%s%s", prefix, datePath, uuid, extension);
    }
}
