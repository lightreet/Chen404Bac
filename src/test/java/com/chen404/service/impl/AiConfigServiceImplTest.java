package com.chen404.service.impl;

import com.chen404.config.AiMaidProperties;
import com.chen404.config.AiRuntimeProperties;
import com.chen404.config.LlmProperties;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiConfigTestRequest;
import com.chen404.domain.dto.AiConfigTestResponse;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.prompt.AiPromptTemplateLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConfigServiceImplTest {

    @Test
    void shouldMaskConfiguredApiKey() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "ai.llm.api_key", "sk-test-abcdef123456"
        ));
        AiConfigServiceImpl service = buildService(mapper, mock(LlmClient.class));

        AiAdminConfigDTO config = service.getAdminConfig();

        assertNull(config.getLlm().getApiKey());
        assertTrue(config.getLlm().getApiKeyConfigured());
        assertEquals("sk-****3456", config.getLlm().getApiKeyPreview());
    }

    @Test
    void shouldPreserveExistingApiKeyWhenPatchApiKeyIsBlank() {
        Map<String, SiteConfig> rows = seedRows(Map.of(
                "ai.llm.api_key", "sk-old-secret"
        ));
        SiteConfigMapper mapper = mapperWithRows(rows);
        AiConfigServiceImpl service = buildService(mapper, mock(LlmClient.class));

        AiAdminConfigDTO patch = service.getAdminConfig();
        patch.getLlm().setModel("gpt-5.4");
        patch.getLlm().setApiKey("");
        service.updateAdminConfig(patch);

        assertEquals("sk-old-secret", value(rows, "ai.llm.api_key"));
        assertEquals("gpt-5.4", value(rows, "ai.llm.model"));
    }

    @Test
    void shouldReplaceApiKeyWhenPatchApiKeyHasText() {
        Map<String, SiteConfig> rows = seedRows(Map.of(
                "ai.llm.api_key", "sk-old-secret"
        ));
        SiteConfigMapper mapper = mapperWithRows(rows);
        AiConfigServiceImpl service = buildService(mapper, mock(LlmClient.class));

        AiAdminConfigDTO patch = service.getAdminConfig();
        patch.getLlm().setApiKey("sk-new-secret");
        service.updateAdminConfig(patch);

        assertEquals("sk-new-secret", value(rows, "ai.llm.api_key"));
    }

    @Test
    void shouldClampUnsafeNumericValues() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of());
        AiConfigServiceImpl service = buildService(mapper, mock(LlmClient.class));

        AiAdminConfigDTO patch = service.getAdminConfig();
        patch.getLlm().setTemperature(9.0);
        patch.getLlm().setMaxTokens(1);
        patch.getLlm().setTimeoutSeconds(999);
        AiAdminConfigDTO saved = service.updateAdminConfig(patch);

        assertEquals(2.0, saved.getLlm().getTemperature());
        assertEquals(128, saved.getLlm().getMaxTokens());
        assertEquals(120, saved.getLlm().getTimeoutSeconds());
    }

    @Test
    void shouldReturnSuccessWhenConnectionTestGeneratesText() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "ai.llm.enabled", "true",
                "ai.llm.api_key", "sk-test",
                "ai.llm.model", "gpt-5.4-mini"
        ));
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("Lyra online");
        AiConfigServiceImpl service = buildService(mapper, llmClient);

        AiConfigTestRequest request = new AiConfigTestRequest();
        request.setMessage("ping");
        AiConfigTestResponse response = service.testConnection(request);

        assertTrue(response.getSuccess());
        assertEquals("连接成功", response.getMessage());
        assertEquals("Lyra online", response.getSampleText());
        assertTrue(response.getLatencyMs() >= 0);
    }

    @Test
    void shouldReturnFailureWhenConnectionTestThrows() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "ai.llm.enabled", "true",
                "ai.llm.api_key", "sk-test"
        ));
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenThrow(new IllegalStateException("bad key"));
        AiConfigServiceImpl service = buildService(mapper, llmClient);

        AiConfigTestResponse response = service.testConnection(new AiConfigTestRequest());

        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("bad key"));
    }

    @Test
    void shouldSendModelOverrideWhenTestingConnection() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "ai.llm.enabled", "true",
                "ai.llm.api_key", "sk-test",
                "ai.llm.model", "gpt-5.4"
        ));
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("ok");
        AiConfigServiceImpl service = buildService(mapper, llmClient);

        service.testConnection(new AiConfigTestRequest());

        ArgumentCaptor<LlmTextRequest> captor = ArgumentCaptor.forClass(LlmTextRequest.class);
        org.mockito.Mockito.verify(llmClient).generateText(captor.capture());
        assertEquals("gpt-5.4", captor.getValue().model());
    }

    @Test
    void shouldExposeDefaultPromptTemplatesWhenAdminPromptOverridesAreBlank() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "ai.maid.system_prompt", "",
                "ai.maid.helper_prompt", "",
                "ai.maid.companion_prompt", ""
        ));
        AiConfigServiceImpl service = buildService(mapper, mock(LlmClient.class));

        AiAdminConfigDTO config = service.getAdminConfig();

        assertTrue(config.getMaid().getSystemPrompt().contains("你叫{{maidName}}"));
        assertTrue(config.getMaid().getHelperPrompt().contains("Task mode: helper"));
        assertTrue(config.getMaid().getCompanionPrompt().contains("Task mode: companion"));
    }

    private AiConfigServiceImpl buildService(SiteConfigMapper mapper, LlmClient llmClient) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setEnabled(false);
        llmProperties.setApiKey("");
        AiRuntimeProperties runtimeProperties = new AiRuntimeProperties();
        AiMaidProperties maidProperties = new AiMaidProperties();
        AiPromptTemplateLoader promptTemplateLoader = new AiPromptTemplateLoader(new DefaultResourceLoader());
        return new AiConfigServiceImpl(mapper, llmProperties, runtimeProperties, maidProperties, promptTemplateLoader, llmClient);
    }

    private SiteConfigMapper mapperWithSeed(Map<String, String> seed) {
        return mapperWithRows(seedRows(seed));
    }

    private SiteConfigMapper mapperWithRows(Map<String, SiteConfig> rows) {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        when(mapper.selectAllConfigs()).thenAnswer(invocation -> new ArrayList<>(rows.values()));
        when(mapper.insert(any(SiteConfig.class))).thenAnswer(invocation -> {
            SiteConfig row = invocation.getArgument(0);
            row.setId((long) rows.size() + 1);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(mapper.updateById(any(SiteConfig.class))).thenAnswer(invocation -> {
            SiteConfig row = invocation.getArgument(0);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        return mapper;
    }

    private Map<String, SiteConfig> seedRows(Map<String, String> seed) {
        Map<String, SiteConfig> rows = new LinkedHashMap<>();
        long[] nextId = {1L};
        seed.forEach((key, value) -> {
            SiteConfig row = new SiteConfig();
            row.setId(nextId[0]++);
            row.setConfigKey(key);
            row.setConfigValue(value);
            rows.put(key, row);
        });
        return rows;
    }

    private String value(Map<String, SiteConfig> rows, String key) {
        SiteConfig row = rows.get(key);
        return row == null ? null : row.getConfigValue();
    }
}
