package com.chen404.service;

import com.chen404.config.MinioConfig;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserTrustRequest;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.MusicTrackMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.SysFileMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.mapper.UserTrustRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 受保护文件的业务鉴权、稳定 URL 归一化与短时访问地址签发。
 */
@Service
public class ProtectedFileAccessService {

    private static final int STORAGE_URL_EXPIRE_MINUTES = 5;
    private static final Pattern MANAGED_URL_PATTERN = Pattern.compile(
            "(?:https?://[^\\s)\"']+)?/api/files/\\d+(?:\\?ticket=[^\\s)\"']+)?"
    );

    private final SysFileMapper sysFileMapper;
    private final ArticleMapper articleMapper;
    private final TravelMemoryLocationMapper travelMemoryLocationMapper;
    private final MusicTrackMapper musicTrackMapper;
    private final ReaderBookMapper readerBookMapper;
    private final UserTrustRequestMapper userTrustRequestMapper;
    private final AccessService accessService;
    private final FileStorageService fileStorageService;
    private final ManagedFileUrlCodec managedFileUrlCodec;
    private final MinioConfig minioConfig;

    public ProtectedFileAccessService(
            SysFileMapper sysFileMapper,
            ArticleMapper articleMapper,
            TravelMemoryLocationMapper travelMemoryLocationMapper,
            MusicTrackMapper musicTrackMapper,
            ReaderBookMapper readerBookMapper,
            UserTrustRequestMapper userTrustRequestMapper,
            AccessService accessService,
            FileStorageService fileStorageService,
            ManagedFileUrlCodec managedFileUrlCodec,
            MinioConfig minioConfig) {
        this.sysFileMapper = sysFileMapper;
        this.articleMapper = articleMapper;
        this.travelMemoryLocationMapper = travelMemoryLocationMapper;
        this.musicTrackMapper = musicTrackMapper;
        this.readerBookMapper = readerBookMapper;
        this.userTrustRequestMapper = userTrustRequestMapper;
        this.accessService = accessService;
        this.fileStorageService = fileStorageService;
        this.managedFileUrlCodec = managedFileUrlCodec;
        this.minioConfig = minioConfig;
    }

    public String resolveDownloadUrl(Long fileId, Long viewerId, String ticket) {
        SysFile file = requireFile(fileId);
        if (!SysFile.StorageScope.PROTECTED.equals(file.getStorageScope())) {
            return file.getFileUrl();
        }
        if (!managedFileUrlCodec.isValidTicket(fileId, ticket) && !canRead(file, viewerId)) {
            throw new ForbiddenException("无权访问该文件");
        }
        String bucketName = StringUtils.hasText(file.getBucketName())
                ? file.getBucketName()
                : minioConfig.getProtectedBucketName();
        return fileStorageService.getPresignedGetUrl(
                bucketName,
                file.getObjectName(),
                STORAGE_URL_EXPIRE_MINUTES
        );
    }

    public String normalizeUrl(String fileUrl) {
        return managedFileUrlCodec.normalize(fileUrl);
    }

    public String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        Matcher matcher = MANAGED_URL_PATTERN.matcher(content);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    normalized,
                    Matcher.quoteReplacement(managedFileUrlCodec.normalize(matcher.group()))
            );
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    public String issueUrlForReference(String fileUrl, String refType, Long refId) {
        Long fileId = managedFileUrlCodec.resolveFileId(fileUrl);
        if (fileId == null) {
            return fileUrl;
        }
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null
                || !SysFile.StorageScope.PROTECTED.equals(file.getStorageScope())
                || !Objects.equals(refType, file.getRefType())
                || !Objects.equals(refId, file.getRefId())) {
            return managedFileUrlCodec.stableUrl(fileId);
        }
        return managedFileUrlCodec.ticketedUrl(fileId);
    }

    public String issueContentUrls(String content, String refType, Long refId) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        Matcher matcher = MANAGED_URL_PATTERN.matcher(content);
        StringBuffer ticketed = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    ticketed,
                    Matcher.quoteReplacement(issueUrlForReference(matcher.group(), refType, refId))
            );
        }
        matcher.appendTail(ticketed);
        return ticketed.toString();
    }

    private SysFile requireFile(Long fileId) {
        SysFile file = fileId == null ? null : sysFileMapper.selectById(fileId);
        if (file == null || SysFile.Status.DELETED.equals(file.getStatus())) {
            throw new ResourceNotFoundException("文件不存在");
        }
        return file;
    }

    private boolean canRead(SysFile file, Long viewerId) {
        if (SysFile.Status.TEMP.equals(file.getStatus())) {
            return Objects.equals(file.getUserId(), viewerId) || isActiveAdmin(viewerId);
        }
        return switch (file.getRefType()) {
            case SysFile.RefType.ARTICLE_CONTENT, SysFile.RefType.ARTICLE_COVER ->
                    canReadArticle(file.getRefId(), viewerId);
            case SysFile.RefType.TRAVEL_MEMORY_IMAGE ->
                    canReadTravelMemory(file.getRefId(), viewerId);
            case SysFile.RefType.MUSIC_AUDIO, SysFile.RefType.MUSIC_COVER ->
                    canReadMusic(file.getRefId(), viewerId);
            case SysFile.RefType.NOVEL_COVER -> canReadReaderBook(file.getRefId(), viewerId);
            case SysFile.RefType.TRUST_REQUEST_ATTACHMENT ->
                    canReadTrustRequest(file.getRefId(), viewerId);
            case SysFile.RefType.AVATAR, SysFile.RefType.SITE_ASSET, SysFile.RefType.SITE_HERO -> true;
            default -> Objects.equals(file.getUserId(), viewerId) || isActiveAdmin(viewerId);
        };
    }

    private boolean canReadReaderBook(Long bookId, Long viewerId) {
        ReaderBook book = bookId == null ? null : readerBookMapper.selectById(bookId);
        return book != null && ("public".equals(book.getVisibility())
                || Objects.equals(book.getOwnerUserId(), viewerId));
    }

    private boolean canReadArticle(Long articleId, Long viewerId) {
        Article article = articleId == null ? null : articleMapper.selectById(articleId);
        return article != null && accessService.canViewArticle(viewerId, article);
    }

    private boolean canReadTravelMemory(Long locationId, Long viewerId) {
        TravelMemoryLocation location = locationId == null
                ? null
                : travelMemoryLocationMapper.selectById(locationId);
        return location != null && accessService.canViewTravelMemory(viewerId, location);
    }

    private boolean canReadMusic(Long trackId, Long viewerId) {
        MusicTrack track = trackId == null ? null : musicTrackMapper.selectById(trackId);
        return track != null
                && (MusicTrack.STATUS_PUBLISHED.equals(track.getStatus())
                || accessService.canManageMusicTrack(viewerId, track));
    }

    private boolean canReadTrustRequest(Long requestId, Long viewerId) {
        UserTrustRequest request = requestId == null ? null : userTrustRequestMapper.selectById(requestId);
        return request != null
                && (Objects.equals(request.getUserId(), viewerId) || isActiveAdmin(viewerId));
    }

    private boolean isActiveAdmin(Long viewerId) {
        User user = accessService.getUserOrNull(viewerId);
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && accessService.isAdmin(user);
    }
}
