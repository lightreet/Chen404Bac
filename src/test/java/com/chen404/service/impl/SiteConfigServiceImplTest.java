package com.chen404.service.impl;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.SysFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteConfigServiceImplTest {

    @Test
    void updateConfigShouldRetainHeroImagePositions() {
        SiteConfigMapper siteConfigMapper = mock(SiteConfigMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        when(siteConfigMapper.selectAllConfigs()).thenReturn(List.of());
        doNothing().when(sysFileService).convertToPermanent(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());

        SiteConfigServiceImpl service = new SiteConfigServiceImpl(new ObjectMapper(), siteConfigMapper, sysFileService);
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
        when(siteConfigMapper.selectAllConfigs()).thenReturn(List.of());
        doNothing().when(sysFileService).convertToPermanent(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());

        SiteConfigServiceImpl service = new SiteConfigServiceImpl(new ObjectMapper(), siteConfigMapper, sysFileService);
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
