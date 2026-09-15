package com.chen404.service;

import com.chen404.config.SiteRuntimeProperties;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.RemoteArticleImageDownloader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleImageImportServiceTest {
    private final AccessService access = mock(AccessService.class);
    private final SysFileService files = mock(SysFileService.class);
    private final SiteRuntimeProperties properties = mock(SiteRuntimeProperties.class);
    private final RemoteArticleImageDownloader downloader = mock(RemoteArticleImageDownloader.class);
    private final ArticleImageImportService service = new ArticleImageImportService(access, files, properties, downloader);

    @Test
    void unauthorizedCreatorCannotTriggerDownload() {
        assertThrows(ForbiddenException.class, () -> service.importImage("https://example.com/image", 3L));
        verifyNoInteractions(downloader, files);
    }

    @Test
    void storesTemporaryArticleImageForCurrentUserAndHonorsSiteLimit() throws Exception {
        when(access.canCreateArticle(3L)).thenReturn(true);
        when(properties.getUploadMaxSize()).thenReturn(1024L);
        when(properties.getUploadAllowTypes()).thenReturn(List.of("png"));
        var image = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        when(downloader.download("https://example.com/image", 1024, List.of("png"))).thenReturn(image);
        SysFile file = new SysFile();
        file.setId(900L);
        file.setFileUrl("/api/files/900?ticket=preview");
        file.setFileName("image.png");
        file.setFileSize(1L);
        when(files.uploadTempFile(image, 3L, SysFile.RefType.ARTICLE_CONTENT)).thenReturn(file);
        assertEquals(file.getFileUrl(), service.importImage("https://example.com/image", 3L).getUrl());
        verify(files).uploadTempFile(image, 3L, SysFile.RefType.ARTICLE_CONTENT);
    }

    @Test
    void downloadFailureReleasesUserSlotAndDoesNotPersistAnything() throws Exception {
        when(access.canCreateArticle(3L)).thenReturn(true);
        when(downloader.download(any(), anyLong(), any())).thenThrow(new IOException("private URL must not be exposed"));
        for (int attempt = 0; attempt < 2; attempt++) {
            BadRequestException failure = assertThrows(BadRequestException.class, () -> service.importImage("https://example.com/image", 3L));
            assertEquals("图片下载失败或超时，可重试或手动上传", failure.getMessage());
        }
        verifyNoInteractions(files);
    }
}
