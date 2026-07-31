package com.chen404.controller;

import com.chen404.config.SiteRuntimeProperties;
import com.chen404.domain.Result;
import com.chen404.domain.dto.SingleFileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.TravelMemoryImageMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadControllerSiteImageTest {

    private static final Long ADMIN_USER_ID = 1L;

    private SysFileService sysFileService;
    private UploadController uploadController;

    @BeforeEach
    void setUp() {
        sysFileService = mock(SysFileService.class);
        SiteRuntimeProperties siteRuntimeProperties = mock(SiteRuntimeProperties.class);
        when(siteRuntimeProperties.getUploadAllowTypes()).thenReturn(List.of("png", "jpg"));

        uploadController = new UploadController(
                sysFileService,
                siteRuntimeProperties,
                mock(TravelMemoryImageMetadataService.class),
                mock(AccessService.class),
                Runnable::run
        );
    }

    @Test
    void siteHeroUploadShouldKeepHeroBusinessType() {
        SingleFileUploadDTO form = imageForm("hero.png");
        when(sysFileService.uploadTempFile(any(), eq(ADMIN_USER_ID), eq(SysFile.RefType.SITE_HERO)))
                .thenReturn(uploadedFile("hero.png", "/minio/chen404/site_hero/hero.png"));

        Result<UploadFileVO> result = uploadController.uploadSiteHero(form, adminUser());

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("/minio/chen404/site_hero/hero.png", result.getData().getUrl());
        verify(sysFileService).uploadTempFile(any(), eq(ADMIN_USER_ID), eq(SysFile.RefType.SITE_HERO));
    }

    @Test
    void siteAssetUploadShouldRemainSiteAssetType() {
        SingleFileUploadDTO form = imageForm("logo.png");
        when(sysFileService.uploadTempFile(any(), eq(ADMIN_USER_ID), eq(SysFile.RefType.SITE_ASSET)))
                .thenReturn(uploadedFile("logo.png", "/minio/chen404/site_asset/logo.png"));

        Result<UploadFileVO> result = uploadController.uploadSiteAsset(form, adminUser());

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        verify(sysFileService).uploadTempFile(any(), eq(ADMIN_USER_ID), eq(SysFile.RefType.SITE_ASSET));
    }

    private SingleFileUploadDTO imageForm(String fileName) {
        SingleFileUploadDTO form = new SingleFileUploadDTO();
        form.setFile(new MockMultipartFile("file", fileName, "image/png", new byte[]{1, 2, 3}));
        return form;
    }

    private SysFile uploadedFile(String fileName, String fileUrl) {
        SysFile file = new SysFile();
        file.setId(10L);
        file.setFileName(fileName);
        file.setFileUrl(fileUrl);
        file.setFileSize(3L);
        return file;
    }

    private AuthenticatedUser adminUser() {
        return new AuthenticatedUser(ADMIN_USER_ID, "admin", "ADMIN");
    }
}
