package com.chen404.controller;

import com.chen404.converter.HomeViewConverter;
import com.chen404.config.SiteOwnerProperties;
import com.chen404.domain.Result;
import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.domain.dto.SiteMemberDTO;
import com.chen404.domain.entity.User;
import com.chen404.service.BannerService;
import com.chen404.service.DevelopmentHistoryService;
import com.chen404.service.SiteConfigService;
import com.chen404.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteControllerConfigExposureTest {

    @Test
    void getSiteConfigShouldExposeHeroImagePositions() {
        BannerService bannerService = mock(BannerService.class);
        SiteConfigService siteConfigService = mock(SiteConfigService.class);
        UserService userService = mock(UserService.class);
        HomeViewConverter homeViewConverter = mock(HomeViewConverter.class);
        DevelopmentHistoryService developmentHistoryService = mock(DevelopmentHistoryService.class);
        SiteController controller = new SiteController(
                bannerService,
                siteConfigService,
                userService,
                homeViewConverter,
                developmentHistoryService,
                mock(SiteOwnerProperties.class));

        SiteConfigDTO config = new SiteConfigDTO();
        config.setHeroImages(new LinkedHashMap<>(Map.of("home", "/hero-home.png")));
        config.setHeroImagePositions(new LinkedHashMap<>(Map.of("home", "50% 58%")));
        when(siteConfigService.getConfig()).thenReturn(config);

        Result<SiteConfigDTO> result = controller.getSiteConfig();

        assertNotNull(result.getData());
        assertEquals("50% 58%", result.getData().getHeroImagePositions().get("home"));
    }

    @Test
    void getSiteMembersShouldOnlyExposeEmailWhenUserOptedIn() {
        User hiddenEmail = new User();
        hiddenEmail.setId(7L);
        hiddenEmail.setEmail("private@example.com");
        hiddenEmail.setEmailPublic(0);
        User publicEmail = new User();
        publicEmail.setId(8L);
        publicEmail.setEmail("public@example.com");
        publicEmail.setEmailPublic(1);

        UserService userService = mock(UserService.class);
        when(userService.listPublicUsers()).thenReturn(List.of(hiddenEmail, publicEmail));
        SiteController controller = new SiteController(
                mock(BannerService.class),
                mock(SiteConfigService.class),
                userService,
                mock(HomeViewConverter.class),
                mock(DevelopmentHistoryService.class),
                mock(SiteOwnerProperties.class)
        );

        List<SiteMemberDTO> members = controller.getSiteMembers().getData();

        assertEquals(2, members.size());
        assertNull(members.get(0).getEmail());
        assertEquals("public@example.com", members.get(1).getEmail());
    }
}
