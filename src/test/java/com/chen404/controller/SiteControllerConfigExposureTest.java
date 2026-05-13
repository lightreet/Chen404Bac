package com.chen404.controller;

import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.service.BannerService;
import com.chen404.service.SiteConfigService;
import com.chen404.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteControllerConfigExposureTest {

    @Test
    void getSiteConfigShouldExposeHeroImagePositions() {
        BannerService bannerService = mock(BannerService.class);
        SiteConfigService siteConfigService = mock(SiteConfigService.class);
        UserService userService = mock(UserService.class);
        HomeViewConverter homeViewConverter = mock(HomeViewConverter.class);
        SiteController controller = new SiteController(bannerService, siteConfigService, userService, homeViewConverter);

        SiteConfigDTO config = new SiteConfigDTO();
        config.setHeroImages(new LinkedHashMap<>(Map.of("home", "/hero-home.png")));
        config.setHeroImagePositions(new LinkedHashMap<>(Map.of("home", "50% 58%")));
        when(siteConfigService.getConfig()).thenReturn(config);

        Result<SiteConfigDTO> result = controller.getSiteConfig();

        assertNotNull(result.getData());
        assertEquals("50% 58%", result.getData().getHeroImagePositions().get("home"));
    }
}
