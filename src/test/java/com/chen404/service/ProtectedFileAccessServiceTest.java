package com.chen404.service;

import com.chen404.config.MinioConfig;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.exception.ForbiddenException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.MusicTrackMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.SysFileMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.mapper.UserTrustRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectedFileAccessServiceTest {

    private SysFileMapper sysFileMapper;
    private ArticleMapper articleMapper;
    private ReaderBookMapper readerBookMapper;
    private AccessService accessService;
    private FileStorageService fileStorageService;
    private ManagedFileUrlCodec codec;
    private ProtectedFileAccessService protectedFileAccessService;

    @BeforeEach
    void setUp() {
        sysFileMapper = mock(SysFileMapper.class);
        articleMapper = mock(ArticleMapper.class);
        readerBookMapper = mock(ReaderBookMapper.class);
        accessService = mock(AccessService.class);
        fileStorageService = mock(FileStorageService.class);
        codec = new ManagedFileUrlCodec("test-protected-file-secret", 5);
        MinioConfig minioConfig = new MinioConfig();
        minioConfig.setProtectedBucketName("protected");
        protectedFileAccessService = new ProtectedFileAccessService(
                sysFileMapper,
                articleMapper,
                mock(TravelMemoryLocationMapper.class),
                mock(MusicTrackMapper.class),
                readerBookMapper,
                mock(UserTrustRequestMapper.class),
                accessService,
                fileStorageService,
                codec,
                minioConfig
        );
    }

    @Test
    void shouldRejectAnonymousDirectAccessToProtectedDraftFile() {
        SysFile file = buildProtectedArticleFile();
        Article draft = new Article();
        draft.setId(99L);
        draft.setAuthorId(7L);
        draft.setStatus(0);
        when(sysFileMapper.selectById(12L)).thenReturn(file);
        when(articleMapper.selectById(99L)).thenReturn(draft);
        when(accessService.canViewArticle(null, draft)).thenReturn(false);

        assertThrows(
                ForbiddenException.class,
                () -> protectedFileAccessService.resolveDownloadUrl(12L, null, null)
        );
    }

    @Test
    void shouldAllowShortLivedTicketIssuedAfterBusinessAuthorization() {
        SysFile file = buildProtectedArticleFile();
        when(sysFileMapper.selectById(12L)).thenReturn(file);
        when(fileStorageService.getPresignedGetUrl("protected", "article/a.webp", 5))
                .thenReturn("https://storage.example.com/signed");

        String ticketedUrl = codec.ticketedUrl(12L);
        String ticket = URI.create(ticketedUrl).getQuery().substring("ticket=".length());

        assertEquals(
                "https://storage.example.com/signed",
                protectedFileAccessService.resolveDownloadUrl(12L, null, ticket)
        );
    }

    @Test
    void shouldAllowAnonymousAccessToPublicReaderBookCover() {
        SysFile file = buildProtectedArticleFile();
        file.setRefType(SysFile.RefType.NOVEL_COVER);
        file.setRefId(23L);
        ReaderBook book = new ReaderBook();
        book.setId(23L);
        book.setOwnerUserId(7L);
        book.setVisibility("public");
        when(sysFileMapper.selectById(12L)).thenReturn(file);
        when(readerBookMapper.selectById(23L)).thenReturn(book);
        when(fileStorageService.getPresignedGetUrl("protected", "article/a.webp", 5))
                .thenReturn("https://storage.example.com/reader-cover");

        assertEquals(
                "https://storage.example.com/reader-cover",
                protectedFileAccessService.resolveDownloadUrl(12L, null, null)
        );
    }

    @Test
    void shouldAllowFriendAccessToFriendVisibleReaderBookCover() {
        SysFile file = buildProtectedArticleFile();
        file.setRefType(SysFile.RefType.NOVEL_COVER);
        file.setRefId(24L);
        ReaderBook book = new ReaderBook();
        book.setId(24L);
        book.setOwnerUserId(7L);
        book.setVisibility("friend");
        when(sysFileMapper.selectById(12L)).thenReturn(file);
        when(readerBookMapper.selectById(24L)).thenReturn(book);
        when(accessService.hasCapability(18L, UserCapabilityEnum.FRIEND_CONTENT_VIEW.getCode())).thenReturn(true);
        when(fileStorageService.getPresignedGetUrl("protected", "article/a.webp", 5))
                .thenReturn("https://storage.example.com/friend-reader-cover");

        assertEquals(
                "https://storage.example.com/friend-reader-cover",
                protectedFileAccessService.resolveDownloadUrl(12L, 18L, null)
        );
    }

    private SysFile buildProtectedArticleFile() {
        SysFile file = new SysFile();
        file.setId(12L);
        file.setStatus(SysFile.Status.PERMANENT);
        file.setStorageScope(SysFile.StorageScope.PROTECTED);
        file.setBucketName("protected");
        file.setObjectName("article/a.webp");
        file.setRefType(SysFile.RefType.ARTICLE_CONTENT);
        file.setRefId(99L);
        file.setFileUrl(codec.stableUrl(12L));
        return file;
    }
}
