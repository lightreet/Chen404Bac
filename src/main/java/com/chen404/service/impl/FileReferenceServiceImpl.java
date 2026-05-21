package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserTrustRequest;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.FileReferenceMapper;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.mapper.TravelMemoryEntryMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.mapper.UserTrustRequestMapper;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一文件引用关系服务。
 * 负责将文章、头像、站点配置、旅行记忆和受信申请等模块中的文件使用情况
 * 同步到 file_reference，便于后台统一查询“文件是否被引用、被谁引用”。
 */
@Slf4j
@Service
public class FileReferenceServiceImpl extends ServiceImpl<FileReferenceMapper, FileReference> implements FileReferenceService {

    private static final Long SITE_CONFIG_REF_ID = 1L;
    private static final String KEY_SITE_LOGO = "site.logo";
    private static final String KEY_SITE_FAVICON = "site.favicon";
    private static final String KEY_HERO_IMAGES = "site.hero_images";

    private final SysFileService sysFileService;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final SiteConfigMapper siteConfigMapper;
    private final TravelMemoryLocationMapper travelMemoryLocationMapper;
    private final TravelMemoryEntryMapper travelMemoryEntryMapper;
    private final UserTrustRequestMapper userTrustRequestMapper;
    private final ObjectMapper objectMapper;

    public FileReferenceServiceImpl(
            SysFileService sysFileService,
            ArticleMapper articleMapper,
            UserMapper userMapper,
            SiteConfigMapper siteConfigMapper,
            TravelMemoryLocationMapper travelMemoryLocationMapper,
            TravelMemoryEntryMapper travelMemoryEntryMapper,
            UserTrustRequestMapper userTrustRequestMapper,
            ObjectMapper objectMapper) {
        this.sysFileService = sysFileService;
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.siteConfigMapper = siteConfigMapper;
        this.travelMemoryLocationMapper = travelMemoryLocationMapper;
        this.travelMemoryEntryMapper = travelMemoryEntryMapper;
        this.userTrustRequestMapper = userTrustRequestMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncArticleReferences(Long articleId, String content, String coverImage) {
        if (articleId == null) {
            return;
        }
        replaceReferences(
                FileReference.ModuleCode.ARTICLE,
                FileReference.BizType.ARTICLE_CONTENT,
                articleId,
                resolveReferences(
                        sysFileService.extractImageUrlsFromContent(content),
                        FileReference.FieldKey.CONTENT,
                        FileReference.SourceType.DIRECT
                )
        );
        replaceReferences(
                FileReference.ModuleCode.ARTICLE,
                FileReference.BizType.ARTICLE_COVER,
                articleId,
                resolveReferences(
                        List.of(coverImage),
                        FileReference.FieldKey.COVER_IMAGE,
                        FileReference.SourceType.DIRECT
                )
        );
        log.debug("[FILE_REFERENCE_SYNC] module=ARTICLE bizId={} contentImages={} hasCover={}",
                articleId,
                sysFileService.extractImageUrlsFromContent(content).size(),
                StringUtils.hasText(coverImage));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUserAvatarReference(Long userId, String avatarUrl) {
        if (userId == null) {
            return;
        }
        replaceReferences(
                FileReference.ModuleCode.USER,
                FileReference.BizType.USER_AVATAR,
                userId,
                resolveReferences(List.of(avatarUrl), FileReference.FieldKey.AVATAR, FileReference.SourceType.DIRECT)
        );
        log.debug("[FILE_REFERENCE_SYNC] module=USER bizId={} hasAvatar={}", userId, StringUtils.hasText(avatarUrl));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncSiteConfigReferences(Long configId, String siteLogo, String siteFavicon, Map<String, String> heroImages) {
        if (configId == null) {
            return;
        }
        Map<String, String> siteAssets = new LinkedHashMap<>();
        siteAssets.put(FileReference.FieldKey.SITE_LOGO, siteLogo);
        siteAssets.put(FileReference.FieldKey.SITE_FAVICON, siteFavicon);
        replaceReferences(
                FileReference.ModuleCode.SITE_CONFIG,
                FileReference.BizType.SITE_ASSET,
                configId,
                resolveNamedReferences(siteAssets, FileReference.SourceType.DIRECT)
        );

        Map<String, String> namedHeroes = new LinkedHashMap<>();
        if (heroImages != null) {
            for (Map.Entry<String, String> entry : heroImages.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                namedHeroes.put(KEY_HERO_IMAGES + "." + entry.getKey().trim(), entry.getValue().trim());
            }
        }
        replaceReferences(
                FileReference.ModuleCode.SITE_CONFIG,
                FileReference.BizType.SITE_HERO,
                configId,
                resolveNamedReferences(namedHeroes, FileReference.SourceType.DIRECT)
        );
        log.debug("[FILE_REFERENCE_SYNC] module=SITE_CONFIG bizId={} heroCount={}",
                configId, heroImages == null ? 0 : heroImages.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTravelMemoryReferences(Long locationId, String coverImage, List<TravelMemoryEntry> entries) {
        if (locationId == null) {
            return;
        }
        replaceReferences(
                FileReference.ModuleCode.TRAVEL_MEMORY,
                FileReference.BizType.TRAVEL_MEMORY_COVER,
                locationId,
                resolveReferences(List.of(coverImage), FileReference.FieldKey.COVER_IMAGE, FileReference.SourceType.DIRECT)
        );
        if (entries == null) {
            return;
        }
        for (TravelMemoryEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            replaceReferences(
                    FileReference.ModuleCode.TRAVEL_MEMORY_ENTRY,
                    FileReference.BizType.TRAVEL_MEMORY_ENTRY_IMAGE,
                    entry.getId(),
                    resolveReferences(
                            List.of(entry.getImageUrl()),
                            FileReference.FieldKey.IMAGE_URL,
                            FileReference.SourceType.DIRECT
                    )
            );
        }
        log.debug("[FILE_REFERENCE_SYNC] module=TRAVEL_MEMORY bizId={} entryCount={} hasCover={}",
                locationId, entries == null ? 0 : entries.size(), StringUtils.hasText(coverImage));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTrustRequestAttachmentReferences(Long requestId, List<String> attachmentUrls) {
        if (requestId == null) {
            return;
        }
        replaceReferences(
                FileReference.ModuleCode.TRUST_REQUEST,
                FileReference.BizType.TRUST_REQUEST_ATTACHMENT,
                requestId,
                resolveReferences(attachmentUrls, FileReference.FieldKey.ATTACHMENTS, FileReference.SourceType.DIRECT)
        );
        log.debug("[FILE_REFERENCE_SYNC] module=TRUST_REQUEST bizId={} attachmentCount={}",
                requestId, attachmentUrls == null ? 0 : attachmentUrls.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByOwner(String moduleCode, String bizType, Long bizId) {
        if (bizId == null || !StringUtils.hasText(moduleCode) || !StringUtils.hasText(bizType)) {
            return;
        }
        remove(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getModuleCode, moduleCode)
                .eq(FileReference::getBizType, bizType)
                .eq(FileReference::getBizId, bizId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByOwners(String moduleCode, String bizType, Collection<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty() || !StringUtils.hasText(moduleCode) || !StringUtils.hasText(bizType)) {
            return;
        }
        List<Long> validIds = bizIds.stream().filter(Objects::nonNull).distinct().toList();
        if (validIds.isEmpty()) {
            return;
        }
        remove(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getModuleCode, moduleCode)
                .eq(FileReference::getBizType, bizType)
                .in(FileReference::getBizId, validIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> rebuildAllReferences() {
        remove(new LambdaQueryWrapper<>());
        log.info("[FILE_REFERENCE_REBUILD] start");

        int articleCount = 0;
        for (Article article : articleMapper.selectList(null)) {
            if (article == null || article.getId() == null) {
                continue;
            }
            String coverImage = article.getCoverImage();
            if (!StringUtils.hasText(coverImage) && article.getCoverFileId() != null) {
                SysFile coverFile = sysFileService.getById(article.getCoverFileId());
                coverImage = coverFile == null ? null : coverFile.getFileUrl();
            }
            syncArticleReferences(article.getId(), article.getContent(), coverImage);
            articleCount++;
        }

        int userCount = 0;
        for (User user : userMapper.selectList(null)) {
            if (user == null || user.getId() == null) {
                continue;
            }
            String avatarUrl = user.getAvatar();
            if (!StringUtils.hasText(avatarUrl) && user.getAvatarFileId() != null) {
                SysFile file = sysFileService.getById(user.getAvatarFileId());
                avatarUrl = file == null ? null : file.getFileUrl();
            }
            syncUserAvatarReference(user.getId(), avatarUrl);
            userCount++;
        }

        syncSiteConfigReferences(
                SITE_CONFIG_REF_ID,
                loadSiteConfigValue(KEY_SITE_LOGO),
                loadSiteConfigValue(KEY_SITE_FAVICON),
                parseHeroImages(loadSiteConfigValue(KEY_HERO_IMAGES))
        );

        int travelLocationCount = 0;
        List<TravelMemoryLocation> locations = travelMemoryLocationMapper.selectList(null);
        Map<Long, List<TravelMemoryEntry>> entryMap = buildTravelEntryMap();
        for (TravelMemoryLocation location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            syncTravelMemoryReferences(
                    location.getId(),
                    location.getCoverImage(),
                    entryMap.getOrDefault(location.getId(), List.of())
            );
            travelLocationCount++;
        }

        int trustRequestCount = 0;
        for (UserTrustRequest request : userTrustRequestMapper.selectList(null)) {
            if (request == null || request.getId() == null) {
                continue;
            }
            List<String> attachmentUrls = sysFileService.list(new LambdaQueryWrapper<SysFile>()
                            .eq(SysFile::getRefType, SysFile.RefType.TRUST_REQUEST_ATTACHMENT)
                            .eq(SysFile::getRefId, request.getId())
                            .ne(SysFile::getStatus, SysFile.Status.DELETED))
                    .stream()
                    .map(SysFile::getFileUrl)
                    .filter(StringUtils::hasText)
                    .toList();
            syncTrustRequestAttachmentReferences(request.getId(), attachmentUrls);
            trustRequestCount++;
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("articles", articleCount);
        summary.put("users", userCount);
        summary.put("travelLocations", travelLocationCount);
        summary.put("trustRequests", trustRequestCount);
        summary.put("references", (int) count());
        log.info("[FILE_REFERENCE_REBUILD] done summary={}", summary);
        return summary;
    }

    private Map<Long, List<TravelMemoryEntry>> buildTravelEntryMap() {
        Map<Long, List<TravelMemoryEntry>> map = new LinkedHashMap<>();
        for (TravelMemoryEntry entry : travelMemoryEntryMapper.selectList(null)) {
            if (entry == null || entry.getLocationId() == null) {
                continue;
            }
            map.computeIfAbsent(entry.getLocationId(), key -> new ArrayList<>()).add(entry);
        }
        return map;
    }

    private String loadSiteConfigValue(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        for (SiteConfig row : siteConfigMapper.selectAllConfigs()) {
            if (row != null && key.equals(row.getConfigKey())) {
                return row.getConfigValue();
            }
        }
        return null;
    }

    private Map<String, String> parseHeroImages(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private void replaceReferences(String moduleCode, String bizType, Long bizId, List<ResolvedReference> references) {
        removeByOwner(moduleCode, bizType, bizId);
        if (bizId == null || references == null || references.isEmpty()) {
            return;
        }

        Map<String, FileReference> dedup = new LinkedHashMap<>();
        for (ResolvedReference reference : references) {
            if (reference == null || reference.fileId() == null || !StringUtils.hasText(reference.fieldKey())) {
                continue;
            }
            String dedupKey = reference.fileId() + "|" + reference.fieldKey();
            if (dedup.containsKey(dedupKey)) {
                continue;
            }
            FileReference row = new FileReference();
            row.setFileId(reference.fileId());
            row.setModuleCode(moduleCode);
            row.setBizType(bizType);
            row.setBizId(bizId);
            row.setFieldKey(reference.fieldKey());
            row.setSourceType(reference.sourceType());
            dedup.put(dedupKey, row);
        }

        if (!dedup.isEmpty()) {
            saveBatch(dedup.values());
        }
    }

    private List<ResolvedReference> resolveReferences(List<String> urls, String fieldKey, String sourceType) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<ResolvedReference> resolved = new ArrayList<>();
        for (String rawUrl : urls) {
            if (!StringUtils.hasText(rawUrl)) {
                continue;
            }
            SysFile file = sysFileService.findByFileUrl(rawUrl.trim());
            if (file == null || file.getId() == null) {
                continue;
            }
            resolved.add(new ResolvedReference(file.getId(), fieldKey, sourceType));
        }
        return resolved;
    }

    private List<ResolvedReference> resolveNamedReferences(Map<String, String> fieldToUrl, String sourceType) {
        if (fieldToUrl == null || fieldToUrl.isEmpty()) {
            return List.of();
        }
        List<ResolvedReference> resolved = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldToUrl.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            SysFile file = sysFileService.findByFileUrl(entry.getValue().trim());
            if (file == null || file.getId() == null) {
                continue;
            }
            resolved.add(new ResolvedReference(file.getId(), entry.getKey().trim(), sourceType));
        }
        return resolved;
    }

    private record ResolvedReference(Long fileId, String fieldKey, String sourceType) {
    }
}
