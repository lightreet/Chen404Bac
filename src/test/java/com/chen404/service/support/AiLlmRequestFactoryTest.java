package com.chen404.service.support;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.service.AiConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiLlmRequestFactoryTest {

    @Test
    void shouldBuildRequestFromEffectiveAdminConfig() {
        AiAdminConfigDTO config = configWithLlm("https://db.example/v1", "gpt-db", "sk-db");
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(config);
        AiLlmRequestFactory factory = new AiLlmRequestFactory(aiConfigService);

        LlmTextRequest request = factory.buildTextRequest("system", "user");

        assertEquals("https://db.example/v1", request.baseUrl());
        assertEquals("gpt-db", request.model());
        assertEquals("sk-db", request.apiKey());
        assertEquals("chat-completions", request.apiStyle());
        assertEquals(0.3, request.temperature());
        assertEquals(1024, request.maxTokens());
        assertEquals(40, request.timeoutSeconds());
    }

    @Test
    void shouldRejectIncompleteEffectiveConfig() {
        AiAdminConfigDTO config = configWithLlm("https://db.example/v1", "gpt-db", "");
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(config);
        AiLlmRequestFactory factory = new AiLlmRequestFactory(aiConfigService);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> factory.buildTextRequest("system", "user")
        );

        assertEquals("LLM_API_KEY 未配置", error.getMessage());
    }

    static AiAdminConfigDTO configWithLlm(String baseUrl, String model, String apiKey) {
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl(baseUrl);
        config.getLlm().setModel(model);
        config.getLlm().setApiKey(apiKey);
        config.getLlm().setApiStyle("chat-completions");
        config.getLlm().setTemperature(0.3);
        config.getLlm().setMaxTokens(1024);
        config.getLlm().setTimeoutSeconds(40);
        return config;
    }
}
