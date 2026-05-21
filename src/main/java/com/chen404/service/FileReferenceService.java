package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.TravelMemoryEntry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FileReferenceService extends IService<FileReference> {

    void syncArticleReferences(Long articleId, String content, String coverImage);

    void syncUserAvatarReference(Long userId, String avatarUrl);

    void syncSiteConfigReferences(Long configId, String siteLogo, String siteFavicon, Map<String, String> heroImages);

    void syncTravelMemoryReferences(Long locationId, String coverImage, List<TravelMemoryEntry> entries);

    void syncTrustRequestAttachmentReferences(Long requestId, List<String> attachmentUrls);

    void removeByOwner(String moduleCode, String bizType, Long bizId);

    void removeByOwners(String moduleCode, String bizType, Collection<Long> bizIds);

    Map<String, Integer> rebuildAllReferences();
}
