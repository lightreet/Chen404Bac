package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.TravelMemoryEntry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 统一文件引用关系服务接口。
 */
public interface FileReferenceService extends IService<FileReference> {

    /** 同步文章正文与封面的文件引用。 */
    void syncArticleReferences(Long articleId, String content, String coverImage);

    /** 同步评论作者头像引用。 */
    void syncCommentAuthorAvatarReference(Long commentId, Long avatarFileId);

    /** 同步用户头像引用。 */
    void syncUserAvatarReference(Long userId, String avatarUrl);

    /** 同步站点配置中的站点资源与 Hero 图片引用。 */
    void syncSiteConfigReferences(Long configId, String siteLogo, String siteFavicon, Map<String, String> heroImages);

    /** 同步旅行记忆地点与条目图片引用。 */
    void syncTravelMemoryReferences(Long locationId, String coverImage, List<TravelMemoryEntry> entries);

    /** 同步好友申请附件引用。 */
    void syncTrustRequestAttachmentReferences(Long requestId, List<String> attachmentUrls);

    /** 同步音乐曲目的音频与封面引用。 */
    void syncMusicTrackReferences(Long trackId, Long audioFileId, String audioUrl, Long coverFileId, String coverUrl);

    /** 同步私人书架原始小说文件引用。 */
    void syncReaderBookReference(Long bookId, Long sourceFileId);

    /** 删除单个业务对象下的引用记录。 */
    void removeByOwner(String moduleCode, String bizType, Long bizId);

    /** 批量删除多个业务对象下的引用记录。 */
    void removeByOwners(String moduleCode, String bizType, Collection<Long> bizIds);

    /** 全量重建系统文件引用关系。 */
    Map<String, Integer> rebuildAllReferences();

    /** 仅重建音乐曲目的文件引用关系，便于历史数据补齐时重复执行。 */
    Map<String, Integer> rebuildMusicTrackReferences();
}
