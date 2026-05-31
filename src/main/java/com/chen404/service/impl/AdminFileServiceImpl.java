package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminFileDetailVO;
import com.chen404.domain.dto.AdminFileReferenceVO;
import com.chen404.domain.dto.AdminFileStatsBucketVO;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.domain.dto.AdminFileVO;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.service.AdminFileService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.chen404.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminFileServiceImpl implements AdminFileService {

    private static final String STATUS_REFERENCED = "REFERENCED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_UNREFERENCED = "UNREFERENCED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String REFERENCED_FILE_IDS_SQL = "select distinct file_id from file_reference";

    private final SysFileService sysFileService;
    private final FileReferenceService fileReferenceService;
    private final UserService userService;

    public AdminFileServiceImpl(
            SysFileService sysFileService,
            FileReferenceService fileReferenceService,
            UserService userService) {
        this.sysFileService = sysFileService;
        this.fileReferenceService = fileReferenceService;
        this.userService = userService;
    }

    @Override
    public PageResult<AdminFileVO> getAdminFiles(
            Integer page,
            Integer size,
            String keyword,
            String status,
            String refType,
            Boolean referenced,
            String referenceStatus) {
        long current = page == null || page < 1 ? 1L : page;
        long pageSize = size == null || size < 1 ? 10L : size;

        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .orderByDesc(SysFile::getCreateTime)
                .orderByDesc(SysFile::getId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(q -> q.like(SysFile::getFileOriginalName, normalizedKeyword)
                    .or()
                    .like(SysFile::getFileName, normalizedKeyword)
                    .or()
                    .like(SysFile::getFileUrl, normalizedKeyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysFile::getStatus, status.trim());
        }
        if (StringUtils.hasText(refType)) {
            wrapper.eq(SysFile::getRefType, refType.trim());
        }
        if (StringUtils.hasText(referenceStatus)) {
            applyReferenceStatusFilter(wrapper, referenceStatus.trim());
        } else {
            applyReferencedFilter(wrapper, referenced);
        }

        Page<SysFile> filePage = sysFileService.page(new Page<>(current, pageSize), wrapper);
        List<SysFile> files = filePage.getRecords();
        if (files.isEmpty()) {
            return PageResult.of(new Page<AdminFileVO>(current, pageSize, 0));
        }

        Map<Long, List<FileReference>> referenceMap = loadReferenceMap(files.stream()
                .map(SysFile::getId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, User> userMap = loadUserMap(files.stream()
                .map(SysFile::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<AdminFileVO> list = files.stream()
                .map(file -> toAdminFileVO(file, referenceMap.getOrDefault(file.getId(), List.of()), findUser(userMap, file)))
                .toList();

        return new PageResult<>(list, filePage.getTotal(), current, pageSize);
    }

    @Override
    public AdminFileDetailVO getAdminFileDetail(Long fileId) {
        SysFile file = sysFileService.getById(fileId);
        if (file == null) {
            throw new ResourceNotFoundException("文件不存在");
        }
        List<FileReference> references = fileReferenceService.list(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getFileId, fileId)
                .orderByAsc(FileReference::getModuleCode)
                .orderByAsc(FileReference::getBizType)
                .orderByAsc(FileReference::getBizId));
        User user = file.getUserId() == null ? null : userService.getById(file.getUserId());

        AdminFileDetailVO detail = new AdminFileDetailVO();
        AdminFileVO base = toAdminFileVO(file, references, user);
        copyBase(detail, base);
        detail.setReferences(references.stream().map(this::toReferenceVO).toList());
        return detail;
    }

    @Override
    public AdminFileStatsVO getAdminFileStats() {
        List<SysFile> files = sysFileService.list(new LambdaQueryWrapper<SysFile>()
                .select(SysFile::getId, SysFile::getStatus, SysFile::getRefType, SysFile::getFileSize));
        List<FileReference> references = fileReferenceService.list(new LambdaQueryWrapper<FileReference>()
                .select(FileReference::getFileId, FileReference::getModuleCode));

        Map<Long, List<FileReference>> referenceMap = groupReferencesByFileId(references);
        Map<String, Long> statusCounts = defaultStatusCounts();
        Map<String, Long> refTypeCounts = new LinkedHashMap<>();
        long totalSize = 0L;

        for (SysFile file : files) {
            if (file == null) {
                continue;
            }
            if (file.getFileSize() != null && file.getFileSize() > 0) {
                totalSize += file.getFileSize();
            }

            String referenceStatus = resolveReferenceStatus(file, referenceMap.getOrDefault(file.getId(), List.of()));
            statusCounts.merge(referenceStatus, 1L, Long::sum);

            if (StringUtils.hasText(file.getRefType())) {
                refTypeCounts.merge(file.getRefType().trim(), 1L, Long::sum);
            }
        }

        AdminFileStatsVO stats = new AdminFileStatsVO();
        stats.setTotalFiles((long) files.size());
        stats.setTotalSize(totalSize);
        stats.setReferencedCount(statusCounts.getOrDefault(STATUS_REFERENCED, 0L));
        stats.setPendingCount(statusCounts.getOrDefault(STATUS_PENDING, 0L));
        stats.setUnreferencedCount(statusCounts.getOrDefault(STATUS_UNREFERENCED, 0L));
        stats.setDeletedCount(statusCounts.getOrDefault(STATUS_DELETED, 0L));
        stats.setReferenceRecordCount((long) references.size());
        stats.setStatusBuckets(buildStatusBuckets(statusCounts));
        stats.setRefTypeBuckets(buildBuckets(refTypeCounts, this::refTypeLabel));
        return stats;
    }

    private Map<Long, List<FileReference>> loadReferenceMap(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        List<FileReference> rows = fileReferenceService.list(new LambdaQueryWrapper<FileReference>()
                .in(FileReference::getFileId, fileIds));
        return groupReferencesByFileId(rows);
    }

    private Map<Long, List<FileReference>> groupReferencesByFileId(List<FileReference> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<FileReference>> map = new LinkedHashMap<>();
        for (FileReference row : rows) {
            if (row == null || row.getFileId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getFileId(), key -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private Map<Long, User> loadUserMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private User findUser(Map<Long, User> userMap, SysFile file) {
        if (userMap == null || userMap.isEmpty() || file == null || file.getUserId() == null) {
            return null;
        }
        return userMap.get(file.getUserId());
    }

    private AdminFileVO toAdminFileVO(SysFile file, List<FileReference> references, User user) {
        AdminFileVO vo = new AdminFileVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileOriginalName(file.getFileOriginalName());
        vo.setFileUrl(file.getFileUrl());
        vo.setObjectName(file.getObjectName());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setUserId(file.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setStatus(file.getStatus());
        vo.setRefType(file.getRefType());
        vo.setRefId(file.getRefId());
        vo.setCreateTime(file.getCreateTime());
        vo.setUpdateTime(file.getUpdateTime());
        vo.setReferenceCount(references == null ? 0 : references.size());
        vo.setReferenceStatus(resolveReferenceStatus(file, references));
        vo.setReferenceModules(extractReferenceModules(references));
        return vo;
    }

    private void copyBase(AdminFileDetailVO target, AdminFileVO source) {
        target.setId(source.getId());
        target.setFileName(source.getFileName());
        target.setFileOriginalName(source.getFileOriginalName());
        target.setFileUrl(source.getFileUrl());
        target.setObjectName(source.getObjectName());
        target.setFileSize(source.getFileSize());
        target.setContentType(source.getContentType());
        target.setUserId(source.getUserId());
        target.setUsername(source.getUsername());
        target.setStatus(source.getStatus());
        target.setRefType(source.getRefType());
        target.setRefId(source.getRefId());
        target.setReferenceStatus(source.getReferenceStatus());
        target.setReferenceCount(source.getReferenceCount());
        target.setReferenceModules(source.getReferenceModules());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private String resolveReferenceStatus(SysFile file, List<FileReference> references) {
        if (file == null) {
            return STATUS_UNREFERENCED;
        }
        if (SysFile.Status.TEMP.equals(file.getStatus())) {
            return STATUS_PENDING;
        }
        if (SysFile.Status.DELETED.equals(file.getStatus())) {
            return STATUS_DELETED;
        }
        if (references != null && !references.isEmpty()) {
            return STATUS_REFERENCED;
        }
        if (SysFile.Status.PERMANENT.equals(file.getStatus())) {
            return STATUS_UNREFERENCED;
        }
        return STATUS_UNKNOWN;
    }

    private void applyReferencedFilter(LambdaQueryWrapper<SysFile> wrapper, Boolean referenced) {
        if (wrapper == null || referenced == null) {
            return;
        }
        if (referenced) {
            wrapper.inSql(SysFile::getId, REFERENCED_FILE_IDS_SQL);
            return;
        }
        wrapper.notInSql(SysFile::getId, REFERENCED_FILE_IDS_SQL);
    }

    private void applyReferenceStatusFilter(LambdaQueryWrapper<SysFile> wrapper, String referenceStatus) {
        if (wrapper == null || !StringUtils.hasText(referenceStatus)) {
            return;
        }
        String normalizedStatus = referenceStatus.trim().toUpperCase(Locale.ROOT);
        switch (normalizedStatus) {
            case STATUS_PENDING -> wrapper.eq(SysFile::getStatus, SysFile.Status.TEMP);
            case STATUS_DELETED -> wrapper.eq(SysFile::getStatus, SysFile.Status.DELETED);
            case STATUS_REFERENCED -> {
                wrapper.eq(SysFile::getStatus, SysFile.Status.PERMANENT);
                wrapper.inSql(SysFile::getId, REFERENCED_FILE_IDS_SQL);
            }
            case STATUS_UNREFERENCED -> {
                wrapper.eq(SysFile::getStatus, SysFile.Status.PERMANENT);
                wrapper.notInSql(SysFile::getId, REFERENCED_FILE_IDS_SQL);
            }
            default -> {
            }
        }
    }

    private List<String> extractReferenceModules(List<FileReference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(references.stream()
                .map(FileReference::getModuleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private AdminFileReferenceVO toReferenceVO(FileReference row) {
        AdminFileReferenceVO vo = new AdminFileReferenceVO();
        vo.setFileId(row.getFileId());
        vo.setModuleCode(row.getModuleCode());
        vo.setBizType(row.getBizType());
        vo.setBizId(row.getBizId());
        vo.setFieldKey(row.getFieldKey());
        vo.setSourceType(row.getSourceType());
        vo.setBizLabel(buildBizLabel(row));
        return vo;
    }

    private String buildBizLabel(FileReference row) {
        if (row == null) {
            return "";
        }
        return switch (row.getModuleCode()) {
            case FileReference.ModuleCode.ARTICLE -> "文章#" + row.getBizId();
            case FileReference.ModuleCode.COMMENT -> "评论#" + row.getBizId();
            case FileReference.ModuleCode.USER -> "用户#" + row.getBizId();
            case FileReference.ModuleCode.SITE_CONFIG -> "站点配置";
            case FileReference.ModuleCode.TRAVEL_MEMORY -> "旅行记忆地点#" + row.getBizId();
            case FileReference.ModuleCode.TRAVEL_MEMORY_ENTRY -> "旅行记忆图片#" + row.getBizId();
            case FileReference.ModuleCode.TRUST_REQUEST -> "好友申请#" + row.getBizId();
            case FileReference.ModuleCode.MUSIC_TRACK -> "音乐曲目#" + row.getBizId();
            default -> row.getModuleCode() + "#" + row.getBizId();
        };
    }

    private Map<String, Long> defaultStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(STATUS_REFERENCED, 0L);
        counts.put(STATUS_PENDING, 0L);
        counts.put(STATUS_UNREFERENCED, 0L);
        counts.put(STATUS_DELETED, 0L);
        return counts;
    }

    private List<AdminFileStatsBucketVO> buildStatusBuckets(Map<String, Long> counts) {
        List<AdminFileStatsBucketVO> buckets = new ArrayList<>();
        buckets.add(toBucket(STATUS_REFERENCED, "Referenced", counts.getOrDefault(STATUS_REFERENCED, 0L)));
        buckets.add(toBucket(STATUS_PENDING, "Pending", counts.getOrDefault(STATUS_PENDING, 0L)));
        buckets.add(toBucket(STATUS_UNREFERENCED, "Unreferenced", counts.getOrDefault(STATUS_UNREFERENCED, 0L)));
        buckets.add(toBucket(STATUS_DELETED, "Deleted", counts.getOrDefault(STATUS_DELETED, 0L)));
        if (counts.getOrDefault(STATUS_UNKNOWN, 0L) > 0) {
            buckets.add(toBucket(STATUS_UNKNOWN, "Unknown", counts.getOrDefault(STATUS_UNKNOWN, 0L)));
        }
        return buckets;
    }

    private List<AdminFileStatsBucketVO> buildBuckets(Map<String, Long> counts, Function<String, String> labelResolver) {
        if (counts == null || counts.isEmpty()) {
            return List.of();
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int compareCount = Long.compare(right.getValue(), left.getValue());
                    if (compareCount != 0) {
                        return compareCount;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .map(entry -> toBucket(entry.getKey(), labelResolver.apply(entry.getKey()), entry.getValue()))
                .toList();
    }

    private AdminFileStatsBucketVO toBucket(String key, String label, Long count) {
        AdminFileStatsBucketVO bucket = new AdminFileStatsBucketVO();
        bucket.setKey(key);
        bucket.setLabel(label);
        bucket.setCount(count == null ? 0L : count);
        return bucket;
    }

    private String refTypeLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "Unknown";
        }
        return switch (value) {
            case SysFile.RefType.ARTICLE_CONTENT -> "Article Content";
            case SysFile.RefType.ARTICLE_COVER -> "Article Cover";
            case SysFile.RefType.SITE_ASSET -> "Site Asset";
            case SysFile.RefType.SITE_HERO -> "Site Hero";
            case SysFile.RefType.AVATAR -> "Avatar";
            case SysFile.RefType.TRUST_REQUEST_ATTACHMENT -> "Trust Request Attachment";
            case SysFile.RefType.TRAVEL_MEMORY_IMAGE -> "Travel Memory Image";
            case SysFile.RefType.MUSIC_AUDIO -> "Music Audio";
            case SysFile.RefType.MUSIC_COVER -> "Music Cover";
            case SysFile.RefType.OTHER -> "Other";
            default -> value;
        };
    }
}
