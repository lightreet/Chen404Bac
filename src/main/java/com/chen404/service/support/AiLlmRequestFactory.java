package com.chen404.service.support;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.service.AiConfigService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds LLM requests from the effective AI admin configuration.
 */
@Component
public class AiLlmRequestFactory {

    private static final String DEFAULT_API_STYLE = "chat-completions";

    private final AiConfigService aiConfigService;

    public AiLlmRequestFactory(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    public LlmTextRequest buildTextRequest(String systemInstruction, String userPrompt) {
        return buildTextRequest(aiConfigService.getEffectiveConfig(), systemInstruction, userPrompt);
    }

    public LlmTextRequest buildTextRequest(AiAdminConfigDTO config, String systemInstruction, String userPrompt) {
        AiAdminConfigDTO.LlmConfig llm = config == null ? null : config.getLlm();
        validate(llm);
        return new LlmTextRequest(
                llm.getModel().trim(),
                systemInstruction,
                userPrompt,
                llm.getTemperature(),
                llm.getMaxTokens(),
                llm.getBaseUrl().trim(),
                llm.getApiKey().trim(),
                normalizeApiStyle(llm.getApiStyle()),
                null,
                null,
                llm.getTimeoutSeconds()
        );
    }

    private void validate(AiAdminConfigDTO.LlmConfig llm) {
        if (llm == null) {
            throw new IllegalStateException("LLM config is missing");
        }
        if (Boolean.FALSE.equals(llm.getEnabled())) {
            throw new IllegalStateException("LLM is disabled by admin config");
        }
        if (!StringUtils.hasText(llm.getBaseUrl())) {
            throw new IllegalStateException("LLM_BASE_URL \u672a\u914d\u7f6e");
        }
        if (!StringUtils.hasText(llm.getModel())) {
            throw new IllegalStateException("LLM_MODEL \u672a\u914d\u7f6e");
        }
        if (!StringUtils.hasText(llm.getApiKey())) {
            throw new IllegalStateException("LLM_API_KEY \u672a\u914d\u7f6e");
        }
    }

    private String normalizeApiStyle(String apiStyle) {
        if (!StringUtils.hasText(apiStyle)) {
            return DEFAULT_API_STYLE;
        }
        String normalized = apiStyle.trim().toLowerCase();
        return "responses".equals(normalized) ? "responses" : DEFAULT_API_STYLE;
    }
}
