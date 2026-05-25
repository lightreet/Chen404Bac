package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiConfigTestRequest;
import com.chen404.domain.dto.AiConfigTestResponse;
import com.chen404.service.AiConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiConfigControllerTest {

    @Test
    void shouldReturnMaskedAdminConfig() {
        AiConfigService service = mock(AiConfigService.class);
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setApiKeyConfigured(true);
        config.getLlm().setApiKeyPreview("sk-****3456");
        config.getLlm().setApiKey(null);
        when(service.getAdminConfig()).thenReturn(config);
        AdminAiConfigController controller = new AdminAiConfigController(service);

        Result<AiAdminConfigDTO> result = controller.getConfig();

        assertTrue(result.getData().getLlm().getApiKeyConfigured());
        assertEquals("sk-****3456", result.getData().getLlm().getApiKeyPreview());
        assertNull(result.getData().getLlm().getApiKey());
    }

    @Test
    void shouldUpdateAdminConfig() {
        AiConfigService service = mock(AiConfigService.class);
        AiAdminConfigDTO request = new AiAdminConfigDTO();
        request.getLlm().setModel("gpt-5.4");
        when(service.updateAdminConfig(any(AiAdminConfigDTO.class))).thenReturn(request);
        AdminAiConfigController controller = new AdminAiConfigController(service);

        Result<AiAdminConfigDTO> result = controller.updateConfig(request);

        assertEquals("gpt-5.4", result.getData().getLlm().getModel());
        verify(service).updateAdminConfig(request);
    }

    @Test
    void shouldTestConnection() {
        AiConfigService service = mock(AiConfigService.class);
        AiConfigTestResponse response = new AiConfigTestResponse();
        response.setSuccess(true);
        response.setMessage("连接成功");
        response.setSampleText("Lyra online");
        when(service.testConnection(any(AiConfigTestRequest.class))).thenReturn(response);
        AdminAiConfigController controller = new AdminAiConfigController(service);

        Result<AiConfigTestResponse> result = controller.testConnection(new AiConfigTestRequest());

        assertTrue(result.getData().getSuccess());
        assertEquals("连接成功", result.getData().getMessage());
        assertEquals("Lyra online", result.getData().getSampleText());
    }
}
