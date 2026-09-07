package com.chen404.service.impl;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.exception.BadRequestException;
import com.chen404.service.support.SiteFrontendAddress;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteConfigServiceImplTest {

    @Test
    void frontendAddressShouldReuseServerDefaultAndPersistAdminOverrideWithSameKey() {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        Map<String, SiteConfig> rows = new LinkedHashMap<>();
        SiteConfig name = new SiteConfig(); name.setId(1L); name.setConfigKey("site.name"); name.setConfigValue("Test");
        rows.put(name.getConfigKey(), name);
        when(mapper.selectAllConfigs()).thenAnswer(call -> List.copyOf(rows.values()));
        when(mapper.insert(any(SiteConfig.class))).thenAnswer(call -> {
            SiteConfig row = call.getArgument(0); row.setId((long) rows.size() + 1); rows.put(row.getConfigKey(), row); return 1;
        });
        when(mapper.updateById(any(SiteConfig.class))).thenAnswer(call -> {
            SiteConfig row = call.getArgument(0); rows.put(row.getConfigKey(), row); return 1;
        });
        var service = new SiteConfigServiceImpl(new ObjectMapper(), mapper, mock(SysFileService.class), mock(FileReferenceService.class));
        ReflectionTestUtils.setField(service, "configuredFrontendBaseUrl", "https://www.chen404.cn/");
        assertEquals("https://www.chen404.cn", service.getConfig().getFrontendBaseUrl());
        SiteConfigDTO unrelatedPatch = new SiteConfigDTO();
        unrelatedPatch.setSiteName("Updated name");
        service.updateConfig(unrelatedPatch);
        ReflectionTestUtils.setField(service, "configuredFrontendBaseUrl", "https://server-new.example.org");
        assertEquals("https://server-new.example.org", service.getConfig().getFrontendBaseUrl());
        ReflectionTestUtils.setField(service, "configuredFrontendBaseUrl", "https://www.chen404.cn/");
        SiteConfigDTO patch = new SiteConfigDTO(); patch.setFrontendBaseUrl(" https://travel.example.org/ ");
        assertEquals("https://travel.example.org", service.updateConfig(patch).getFrontendBaseUrl());
        assertEquals("https://travel.example.org", rows.get("app.frontend-base-url").getConfigValue());
        assertEquals("https://travel.example.org", service.getConfig().getFrontendBaseUrl());
        patch.setFrontendBaseUrl("");
        assertEquals("https://www.chen404.cn", service.updateConfig(patch).getFrontendBaseUrl());
        assertEquals("", rows.get("app.frontend-base-url").getConfigValue());
        assertEquals("https://www.chen404.cn", service.getConfig().getFrontendBaseUrl());
    }

    @Test
    void frontendAddressShouldRejectCredentialsPathsAndNonWebsiteSchemes() {
        for (String invalid : List.of("javascript:alert(1)", "https://name:secret@example.org", "https://example.org/api",
                "https://example.org?token=x", "https://example.org#fragment", "https://example.org:70000")) {
            assertThrows(BadRequestException.class, () -> SiteFrontendAddress.normalize(invalid));
        }
        assertThrows(BadRequestException.class, () -> SiteFrontendAddress.forMobileUpload("http://localhost:20204"));
        assertThrows(BadRequestException.class, () -> SiteFrontendAddress.forMobileUpload("http://127.0.0.2:20204"));
    }

    @Test
    void updateConfigShouldRetainHeroImagePositions() {
        SiteConfigMapper siteConfigMapper = mock(SiteConfigMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        when(siteConfigMapper.selectAllConfigs()).thenReturn(List.of());

        SiteConfigServiceImpl service = new SiteConfigServiceImpl(new ObjectMapper(), siteConfigMapper, sysFileService, fileReferenceService);
        SiteConfigDTO patch = new SiteConfigDTO();
        patch.setHeroImages(new LinkedHashMap<>(Map.of("home", "/hero-home.png")));
        patch.setHeroImagePositions(new LinkedHashMap<>(Map.of(
                "home", "32% 61%",
                "archive", ""
        )));

        SiteConfigDTO result = service.updateConfig(patch);

        assertEquals("/hero-home.png", result.getHeroImages().get("home"));
        assertEquals("32% 61%", result.getHeroImagePositions().get("home"));
        assertFalse(result.getHeroImagePositions().containsKey("archive"));
    }

    @Test
    void updateConfigShouldTrimAndRetainHeroTexts() {
        SiteConfigMapper siteConfigMapper = mock(SiteConfigMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        when(siteConfigMapper.selectAllConfigs()).thenReturn(List.of());

        SiteConfigServiceImpl service = new SiteConfigServiceImpl(new ObjectMapper(), siteConfigMapper, sysFileService, fileReferenceService);
        SiteConfigDTO patch = new SiteConfigDTO();
        patch.setHeroTexts(new LinkedHashMap<>(Map.of(
                "memory-map.title", "  旅行纪念地图  ",
                "memory-map.subtitle", "  把走过的每一段旅程都收藏起来。  ",
                "memory-map.eyebrow", ""
        )));

        SiteConfigDTO result = service.updateConfig(patch);

        assertEquals("旅行纪念地图", result.getHeroTexts().get("memory-map.title"));
        assertEquals("把走过的每一段旅程都收藏起来。", result.getHeroTexts().get("memory-map.subtitle"));
        assertFalse(result.getHeroTexts().containsKey("memory-map.eyebrow"));
    }
}
