package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.FeatureToggleConfigDTO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.FeatureToggleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminFeatureToggleControllerTest {

    @Test
    void shouldReturnCompleteAdminConfig() {
        FeatureToggleService service = mock(FeatureToggleService.class);
        FeatureToggleConfigDTO config = new FeatureToggleConfigDTO();
        config.setArticleCreationEnabled(true);
        config.setMusicCreationEnabled(false);
        when(service.getAdminConfig()).thenReturn(config);
        AdminFeatureToggleController controller = new AdminFeatureToggleController(service);

        Result<FeatureToggleConfigDTO> result = controller.getConfig();

        assertTrue(result.getData().getArticleCreationEnabled());
        assertFalse(result.getData().getMusicCreationEnabled());
    }

    @Test
    void shouldUpdateConfigWithCurrentAdminId() {
        FeatureToggleService service = mock(FeatureToggleService.class);
        FeatureToggleConfigDTO request = new FeatureToggleConfigDTO();
        request.setTravelCreationEnabled(false);
        when(service.updateAdminConfig(request, 7L)).thenReturn(request);
        AdminFeatureToggleController controller = new AdminFeatureToggleController(service);

        Result<FeatureToggleConfigDTO> result = controller.updateConfig(
                request,
                new AuthenticatedUser(7L, "admin", "ADMIN"));

        assertFalse(result.getData().getTravelCreationEnabled());
        verify(service).updateAdminConfig(request, 7L);
    }
}
