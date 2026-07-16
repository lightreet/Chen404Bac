package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.DevelopmentHistoryVO;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.service.DevelopmentHistoryService;
import com.chen404.service.GitHubDevelopmentConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminGitHubDevelopmentConfigControllerTest {

    @Test
    void shouldReturnMaskedConfig() {
        GitHubDevelopmentConfigService configService = mock(GitHubDevelopmentConfigService.class);
        DevelopmentHistoryService historyService = mock(DevelopmentHistoryService.class);
        GitHubDevelopmentAdminConfigDTO config = new GitHubDevelopmentAdminConfigDTO();
        config.setTokenConfigured(true);
        config.setTokenPreview("gith****3456");
        when(configService.getAdminConfig()).thenReturn(config);
        AdminGitHubDevelopmentConfigController controller =
                new AdminGitHubDevelopmentConfigController(configService, historyService);

        Result<GitHubDevelopmentAdminConfigDTO> result = controller.getConfig();

        assertTrue(result.getData().getTokenConfigured());
        assertEquals("gith****3456", result.getData().getTokenPreview());
        assertNull(result.getData().getToken());
    }

    @Test
    void shouldUpdateConfig() {
        GitHubDevelopmentConfigService configService = mock(GitHubDevelopmentConfigService.class);
        DevelopmentHistoryService historyService = mock(DevelopmentHistoryService.class);
        GitHubDevelopmentAdminConfigDTO request = new GitHubDevelopmentAdminConfigDTO();
        request.setOwner("chen404-owner");
        when(configService.updateAdminConfig(request)).thenReturn(request);
        AdminGitHubDevelopmentConfigController controller =
                new AdminGitHubDevelopmentConfigController(configService, historyService);

        Result<GitHubDevelopmentAdminConfigDTO> result = controller.updateConfig(request);

        assertEquals("chen404-owner", result.getData().getOwner());
        verify(configService).updateAdminConfig(request);
    }

    @Test
    void shouldRefreshDevelopmentHistory() {
        GitHubDevelopmentConfigService configService = mock(GitHubDevelopmentConfigService.class);
        DevelopmentHistoryService historyService = mock(DevelopmentHistoryService.class);
        DevelopmentHistoryVO history = new DevelopmentHistoryVO();
        history.setTotalCommits(12);
        when(historyService.refreshDevelopmentHistory()).thenReturn(history);
        AdminGitHubDevelopmentConfigController controller =
                new AdminGitHubDevelopmentConfigController(configService, historyService);

        Result<DevelopmentHistoryVO> result = controller.refresh();

        assertEquals(12, result.getData().getTotalCommits());
        verify(historyService).refreshDevelopmentHistory();
    }
}
