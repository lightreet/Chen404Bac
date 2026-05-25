package com.chen404.service.impl;

import com.chen404.config.AiMaidProperties;
import com.chen404.config.AiRuntimeProperties;
import com.chen404.config.LlmProperties;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiConfigTestRequest;
import com.chen404.domain.dto.AiConfigTestResponse;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.AiConfigService;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库驱动的 AI 后台配置服务。
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final String KEY_LLM_ENABLED = "ai.llm.enabled";
    private static final String KEY_LLM_BASE_URL = "ai.llm.base_url";
    private static final String KEY_LLM_MODEL = "ai.llm.model";
    private static final String KEY_LLM_API_STYLE = "ai.llm.api_style";
    private static final String KEY_LLM_API_KEY = "ai.llm.api_key";
    private static final String KEY_LLM_TEMPERATURE = "ai.llm.temperature";
    private static final String KEY_LLM_MAX_TOKENS = "ai.llm.max_tokens";
    private static final String KEY_LLM_TIMEOUT_SECONDS = "ai.llm.timeout_seconds";
    private static final String KEY_MAID_NAME = "ai.maid.name";
    private static final String KEY_MAID_PERSONA_VERSION = "ai.maid.persona_version";
    private static final String KEY_MAID_SYSTEM_PROMPT = "ai.maid.system_prompt";
    private static final String KEY_MAID_HELPER_PROMPT = "ai.maid.helper_prompt";
    private static final String KEY_MAID_COMPANION_PROMPT = "ai.maid.companion_prompt";
    private static final String KEY_CHAT_ENABLED = "ai.chat.enabled";
    private static final String KEY_CHAT_RETRIEVAL_ENABLED = "ai.chat.retrieval_enabled";
    private static final String KEY_CHAT_MAX_CITATION_COUNT = "ai.chat.max_citation_count";
    private static final String KEY_CHAT_MAX_CONTEXT_MESSAGES = "ai.chat.max_context_messages";
    private static final String KEY_CHAT_MAX_ARTICLE_CONTENT_CHARS = "ai.chat.max_article_content_chars";
    private static final String KEY_CHAT_MAX_ARTICLE_SUMMARY_CHARS = "ai.chat.max_article_summary_chars";
    private static final String KEY_CHAT_MAX_SUGGESTION_COUNT = "ai.chat.max_suggestion_count";
    private static final String KEY_CHAT_RELATED_ARTICLE_LIMIT = "ai.chat.related_article_limit";
    private static final String KEY_CHAT_REQUIRE_RECOMMEND_INTENT = "ai.chat.require_recommend_intent_for_related_articles";
    private static final String KEY_CHAT_BUBBLE_MAX_CHARS = "ai.chat.bubble_max_chars";
    private static final String KEY_CHAT_BUBBLE_LONG_REPLY_TEXT = "ai.chat.bubble_long_reply_text";
    private static final String KEY_TOOLS_WEB_SEARCH_ENABLED = "ai.tools.web_search_enabled";
    private static final String DEFAULT_BUBBLE_LONG_REPLY_TEXT = "我整理好了，打开聊天框看详细内容吧。";

    private final SiteConfigMapper siteConfigMapper;
    private final LlmProperties llmProperties;
    private final AiRuntimeProperties aiRuntimeProperties;
    private final AiMaidProperties aiMaidProperties;
    private final LlmClient llmClient;

    public AiConfigServiceImpl(
            SiteConfigMapper siteConfigMapper,
            LlmProperties llmProperties,
            AiRuntimeProperties aiRuntimeProperties,
            AiMaidProperties aiMaidProperties,
            LlmClient llmClient) {
        this.siteConfigMapper = siteConfigMapper;
        this.llmProperties = llmProperties;
        this.aiRuntimeProperties = aiRuntimeProperties;
        this.aiMaidProperties = aiMaidProperties;
        this.llmClient = llmClient;
    }

    @Override
    public AiAdminConfigDTO getAdminConfig() {
        return sanitizeForAdmin(getEffectiveConfig());
    }

    @Override
    public AiAdminConfigDTO updateAdminConfig(AiAdminConfigDTO patch) {
        AiAdminConfigDTO current = getEffectiveConfig();
        String currentApiKey = current.getLlm().getApiKey();
        applyPatch(current, patch);
        normalize(current, currentApiKey);
        writeToDatabase(current);
        return sanitizeForAdmin(current);
    }

    @Override
    public AiAdminConfigDTO getEffectiveConfig() {
        AiAdminConfigDTO config = defaults();
        Map<String, String> rows = loadConfigValues();
        applyRows(config, rows);
        normalize(config, config.getLlm().getApiKey());
        return config;
    }

    @Override
    public AiConfigTestResponse testConnection(AiConfigTestRequest request) {
        long startedAt = System.currentTimeMillis();
        String traceId = "trace_ai_config_" + UUID.randomUUID().toString().replace("-", "");
        AiAdminConfigDTO effective = request != null
                && Boolean.TRUE.equals(request.getUseUnsavedConfig())
                && request.getConfig() != null
                ? mergeWithDefaults(request.getConfig())
                : getEffectiveConfig();
        String message = request != null && StringUtils.hasText(request.getMessage())
                ? request.getMessage().trim()
                : "请用一句话介绍你自己。";
        try {
            String sample = llmClient.generateText(buildTestRequest(effective, message));
            AiConfigTestResponse response = new AiConfigTestResponse();
            response.setSuccess(true);
            response.setMessage("连接成功");
            response.setSampleText(sample);
            response.setTraceId(traceId);
            response.setLatencyMs(System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException ex) {
            AiConfigTestResponse response = new AiConfigTestResponse();
            response.setSuccess(false);
            response.setMessage("连接失败：" + ex.getMessage());
            response.setTraceId(traceId);
            response.setLatencyMs(System.currentTimeMillis() - startedAt);
            return response;
        }
    }

    private LlmTextRequest buildTestRequest(AiAdminConfigDTO config, String message) {
        return new LlmTextRequest(
                config.getLlm().getModel(),
                "你是 Chen404 后台 AI 配置连通性测试助手。请用简短中文回答。",
                message,
                config.getLlm().getTemperature(),
                config.getLlm().getMaxTokens(),
                config.getLlm().getBaseUrl(),
                config.getLlm().getApiKey(),
                config.getLlm().getApiStyle(),
                null,
                null,
                config.getLlm().getTimeoutSeconds()
        );
    }

    private AiAdminConfigDTO mergeWithDefaults(AiAdminConfigDTO patch) {
        AiAdminConfigDTO config = getEffectiveConfig();
        String currentApiKey = config.getLlm().getApiKey();
        applyPatch(config, patch);
        normalize(config, currentApiKey);
        return config;
    }

    private AiAdminConfigDTO defaults() {
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(llmProperties.isEnabled());
        config.getLlm().setBaseUrl(defaultText(llmProperties.getBaseUrl(), "https://api.openai.com/v1"));
        config.getLlm().setModel(defaultText(llmProperties.getModel(), "gpt-5.4-mini"));
        config.getLlm().setApiStyle(defaultText(llmProperties.getApiStyle(), "chat-completions"));
        config.getLlm().setApiKey(defaultText(llmProperties.getApiKey(), ""));
        config.getLlm().setTemperature(llmProperties.getTemperature());
        config.getLlm().setMaxTokens(llmProperties.getMaxTokens());
        config.getLlm().setTimeoutSeconds(llmProperties.getTimeoutSeconds());

        config.getMaid().setName(defaultText(aiMaidProperties.getName(), "Lyra"));
        config.getMaid().setPersonaVersion(defaultText(aiMaidProperties.getPersonaVersion(), "v1.1"));
        config.getMaid().setSystemPrompt("");
        config.getMaid().setHelperPrompt("");
        config.getMaid().setCompanionPrompt("");

        AiRuntimeProperties.Chat chat = aiRuntimeProperties.getChat();
        config.getChat().setEnabled(chat.isEnabled());
        config.getChat().setRetrievalEnabled(true);
        config.getChat().setMaxCitationCount(chat.getMaxCitationCount());
        config.getChat().setMaxContextMessages(chat.getMaxContextMessages());
        config.getChat().setMaxArticleContentChars(chat.getMaxArticleContentChars());
        config.getChat().setMaxArticleSummaryChars(chat.getMaxArticleSummaryChars());
        config.getChat().setMaxSuggestionCount(chat.getMaxSuggestionCount());
        config.getChat().setRelatedArticleLimit(chat.getRelatedArticleLimit());
        config.getChat().setRequireRecommendIntentForRelatedArticles(chat.isRequireRecommendIntentForRelatedArticles());
        config.getChat().setBubbleMaxChars(36);
        config.getChat().setBubbleLongReplyText(DEFAULT_BUBBLE_LONG_REPLY_TEXT);

        config.getTools().setWebSearchEnabled(false);
        return config;
    }

    private Map<String, String> loadConfigValues() {
        return siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        row -> row.getConfigValue() == null ? "" : row.getConfigValue(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void applyRows(AiAdminConfigDTO config, Map<String, String> rows) {
        config.getLlm().setEnabled(parseBoolean(rows.get(KEY_LLM_ENABLED), config.getLlm().getEnabled()));
        config.getLlm().setBaseUrl(textOrDefault(rows.get(KEY_LLM_BASE_URL), config.getLlm().getBaseUrl()));
        config.getLlm().setModel(textOrDefault(rows.get(KEY_LLM_MODEL), config.getLlm().getModel()));
        config.getLlm().setApiStyle(textOrDefault(rows.get(KEY_LLM_API_STYLE), config.getLlm().getApiStyle()));
        config.getLlm().setApiKey(textOrDefault(rows.get(KEY_LLM_API_KEY), config.getLlm().getApiKey()));
        config.getLlm().setTemperature(parseDouble(rows.get(KEY_LLM_TEMPERATURE), config.getLlm().getTemperature()));
        config.getLlm().setMaxTokens(parseInt(rows.get(KEY_LLM_MAX_TOKENS), config.getLlm().getMaxTokens()));
        config.getLlm().setTimeoutSeconds(parseInt(rows.get(KEY_LLM_TIMEOUT_SECONDS), config.getLlm().getTimeoutSeconds()));

        config.getMaid().setName(textOrDefault(rows.get(KEY_MAID_NAME), config.getMaid().getName()));
        config.getMaid().setPersonaVersion(textOrDefault(rows.get(KEY_MAID_PERSONA_VERSION), config.getMaid().getPersonaVersion()));
        config.getMaid().setSystemPrompt(textOrDefault(rows.get(KEY_MAID_SYSTEM_PROMPT), config.getMaid().getSystemPrompt()));
        config.getMaid().setHelperPrompt(textOrDefault(rows.get(KEY_MAID_HELPER_PROMPT), config.getMaid().getHelperPrompt()));
        config.getMaid().setCompanionPrompt(textOrDefault(rows.get(KEY_MAID_COMPANION_PROMPT), config.getMaid().getCompanionPrompt()));

        config.getChat().setEnabled(parseBoolean(rows.get(KEY_CHAT_ENABLED), config.getChat().getEnabled()));
        config.getChat().setRetrievalEnabled(parseBoolean(rows.get(KEY_CHAT_RETRIEVAL_ENABLED), config.getChat().getRetrievalEnabled()));
        config.getChat().setMaxCitationCount(parseInt(rows.get(KEY_CHAT_MAX_CITATION_COUNT), config.getChat().getMaxCitationCount()));
        config.getChat().setMaxContextMessages(parseInt(rows.get(KEY_CHAT_MAX_CONTEXT_MESSAGES), config.getChat().getMaxContextMessages()));
        config.getChat().setMaxArticleContentChars(parseInt(rows.get(KEY_CHAT_MAX_ARTICLE_CONTENT_CHARS), config.getChat().getMaxArticleContentChars()));
        config.getChat().setMaxArticleSummaryChars(parseInt(rows.get(KEY_CHAT_MAX_ARTICLE_SUMMARY_CHARS), config.getChat().getMaxArticleSummaryChars()));
        config.getChat().setMaxSuggestionCount(parseInt(rows.get(KEY_CHAT_MAX_SUGGESTION_COUNT), config.getChat().getMaxSuggestionCount()));
        config.getChat().setRelatedArticleLimit(parseInt(rows.get(KEY_CHAT_RELATED_ARTICLE_LIMIT), config.getChat().getRelatedArticleLimit()));
        config.getChat().setRequireRecommendIntentForRelatedArticles(parseBoolean(rows.get(KEY_CHAT_REQUIRE_RECOMMEND_INTENT), config.getChat().getRequireRecommendIntentForRelatedArticles()));
        config.getChat().setBubbleMaxChars(parseInt(rows.get(KEY_CHAT_BUBBLE_MAX_CHARS), config.getChat().getBubbleMaxChars()));
        config.getChat().setBubbleLongReplyText(textOrDefault(rows.get(KEY_CHAT_BUBBLE_LONG_REPLY_TEXT), config.getChat().getBubbleLongReplyText()));
        config.getTools().setWebSearchEnabled(parseBoolean(rows.get(KEY_TOOLS_WEB_SEARCH_ENABLED), config.getTools().getWebSearchEnabled()));
    }

    private void applyPatch(AiAdminConfigDTO target, AiAdminConfigDTO patch) {
        if (patch == null) {
            return;
        }
        if (patch.getLlm() != null) {
            target.setLlm(patch.getLlm());
        }
        if (patch.getMaid() != null) {
            target.setMaid(patch.getMaid());
        }
        if (patch.getChat() != null) {
            target.setChat(patch.getChat());
        }
        if (patch.getTools() != null) {
            target.setTools(patch.getTools());
        }
    }

    private void normalize(AiAdminConfigDTO config, String currentApiKey) {
        config.getLlm().setEnabled(Boolean.TRUE.equals(config.getLlm().getEnabled()));
        config.getLlm().setBaseUrl(defaultText(config.getLlm().getBaseUrl(), "https://api.openai.com/v1"));
        config.getLlm().setModel(defaultText(config.getLlm().getModel(), "gpt-5.4-mini"));
        config.getLlm().setApiStyle(normalizeApiStyle(config.getLlm().getApiStyle()));
        String patchApiKey = config.getLlm().getApiKey();
        config.getLlm().setApiKey(StringUtils.hasText(patchApiKey) ? patchApiKey.trim() : defaultText(currentApiKey, ""));
        config.getLlm().setTemperature(clampDouble(config.getLlm().getTemperature(), 0.2, 0.0, 2.0));
        config.getLlm().setMaxTokens(clampInt(config.getLlm().getMaxTokens(), 512, 128, 8192));
        config.getLlm().setTimeoutSeconds(clampInt(config.getLlm().getTimeoutSeconds(), 30, 5, 120));

        config.getMaid().setName(defaultText(config.getMaid().getName(), "Lyra"));
        config.getMaid().setPersonaVersion(defaultText(config.getMaid().getPersonaVersion(), "v1.1"));
        config.getMaid().setSystemPrompt(trimToEmpty(config.getMaid().getSystemPrompt()));
        config.getMaid().setHelperPrompt(trimToEmpty(config.getMaid().getHelperPrompt()));
        config.getMaid().setCompanionPrompt(trimToEmpty(config.getMaid().getCompanionPrompt()));

        config.getChat().setEnabled(config.getChat().getEnabled() == null || config.getChat().getEnabled());
        config.getChat().setRetrievalEnabled(config.getChat().getRetrievalEnabled() == null || config.getChat().getRetrievalEnabled());
        config.getChat().setMaxCitationCount(clampInt(config.getChat().getMaxCitationCount(), 3, 0, 8));
        config.getChat().setMaxContextMessages(clampInt(config.getChat().getMaxContextMessages(), 8, 1, 20));
        config.getChat().setMaxArticleContentChars(clampInt(config.getChat().getMaxArticleContentChars(), 3000, 500, 12000));
        config.getChat().setMaxArticleSummaryChars(clampInt(config.getChat().getMaxArticleSummaryChars(), 300, 80, 1000));
        config.getChat().setMaxSuggestionCount(clampInt(config.getChat().getMaxSuggestionCount(), 3, 0, 5));
        config.getChat().setRelatedArticleLimit(clampInt(config.getChat().getRelatedArticleLimit(), 2, 0, 6));
        config.getChat().setRequireRecommendIntentForRelatedArticles(Boolean.TRUE.equals(config.getChat().getRequireRecommendIntentForRelatedArticles()));
        config.getChat().setBubbleMaxChars(clampInt(config.getChat().getBubbleMaxChars(), 36, 12, 60));
        config.getChat().setBubbleLongReplyText(defaultText(config.getChat().getBubbleLongReplyText(), DEFAULT_BUBBLE_LONG_REPLY_TEXT));
        config.getTools().setWebSearchEnabled(Boolean.TRUE.equals(config.getTools().getWebSearchEnabled()));

        applyApiKeyStatus(config);
    }

    private void writeToDatabase(AiAdminConfigDTO config) {
        Map<String, SiteConfig> existing = siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        upsertValue(existing, KEY_LLM_ENABLED, String.valueOf(config.getLlm().getEnabled()), "AI model call enabled", 3);
        upsertValue(existing, KEY_LLM_BASE_URL, config.getLlm().getBaseUrl(), "AI model base URL", 1);
        upsertValue(existing, KEY_LLM_MODEL, config.getLlm().getModel(), "AI model name", 1);
        upsertValue(existing, KEY_LLM_API_STYLE, config.getLlm().getApiStyle(), "AI API style", 1);
        upsertValue(existing, KEY_LLM_API_KEY, config.getLlm().getApiKey(), "AI API key", 1);
        upsertValue(existing, KEY_LLM_TEMPERATURE, String.valueOf(config.getLlm().getTemperature()), "AI temperature", 2);
        upsertValue(existing, KEY_LLM_MAX_TOKENS, String.valueOf(config.getLlm().getMaxTokens()), "AI max tokens", 2);
        upsertValue(existing, KEY_LLM_TIMEOUT_SECONDS, String.valueOf(config.getLlm().getTimeoutSeconds()), "AI timeout seconds", 2);
        upsertValue(existing, KEY_MAID_NAME, config.getMaid().getName(), "AI maid name", 1);
        upsertValue(existing, KEY_MAID_PERSONA_VERSION, config.getMaid().getPersonaVersion(), "AI maid persona version", 1);
        upsertValue(existing, KEY_MAID_SYSTEM_PROMPT, config.getMaid().getSystemPrompt(), "AI maid system prompt", 1);
        upsertValue(existing, KEY_MAID_HELPER_PROMPT, config.getMaid().getHelperPrompt(), "AI maid helper prompt", 1);
        upsertValue(existing, KEY_MAID_COMPANION_PROMPT, config.getMaid().getCompanionPrompt(), "AI maid companion prompt", 1);
        upsertValue(existing, KEY_CHAT_ENABLED, String.valueOf(config.getChat().getEnabled()), "AI chat enabled", 3);
        upsertValue(existing, KEY_CHAT_RETRIEVAL_ENABLED, String.valueOf(config.getChat().getRetrievalEnabled()), "AI chat retrieval enabled", 3);
        upsertValue(existing, KEY_CHAT_MAX_CITATION_COUNT, String.valueOf(config.getChat().getMaxCitationCount()), "AI chat max citation count", 2);
        upsertValue(existing, KEY_CHAT_MAX_CONTEXT_MESSAGES, String.valueOf(config.getChat().getMaxContextMessages()), "AI chat max context messages", 2);
        upsertValue(existing, KEY_CHAT_MAX_ARTICLE_CONTENT_CHARS, String.valueOf(config.getChat().getMaxArticleContentChars()), "AI chat max article content chars", 2);
        upsertValue(existing, KEY_CHAT_MAX_ARTICLE_SUMMARY_CHARS, String.valueOf(config.getChat().getMaxArticleSummaryChars()), "AI chat max article summary chars", 2);
        upsertValue(existing, KEY_CHAT_MAX_SUGGESTION_COUNT, String.valueOf(config.getChat().getMaxSuggestionCount()), "AI chat max suggestion count", 2);
        upsertValue(existing, KEY_CHAT_RELATED_ARTICLE_LIMIT, String.valueOf(config.getChat().getRelatedArticleLimit()), "AI chat related article limit", 2);
        upsertValue(existing, KEY_CHAT_REQUIRE_RECOMMEND_INTENT, String.valueOf(config.getChat().getRequireRecommendIntentForRelatedArticles()), "AI chat require recommend intent", 3);
        upsertValue(existing, KEY_CHAT_BUBBLE_MAX_CHARS, String.valueOf(config.getChat().getBubbleMaxChars()), "AI chat bubble max chars", 2);
        upsertValue(existing, KEY_CHAT_BUBBLE_LONG_REPLY_TEXT, config.getChat().getBubbleLongReplyText(), "AI chat long bubble text", 1);
        upsertValue(existing, KEY_TOOLS_WEB_SEARCH_ENABLED, String.valueOf(config.getTools().getWebSearchEnabled()), "AI web search enabled", 3);
    }

    private void upsertValue(Map<String, SiteConfig> existing, String key, String value, String description, int type) {
        SiteConfig row = existing.get(key);
        if (row == null) {
            row = new SiteConfig();
            row.setConfigKey(key);
            row.setDescription(description);
            row.setConfigType(type);
            row.setIsSystem(1);
            row.setIsPublic(0);
        }
        row.setConfigValue(value);
        row.setIsPublic(0);
        if (row.getId() == null) {
            siteConfigMapper.insert(row);
            existing.put(key, row);
        } else {
            siteConfigMapper.updateById(row);
        }
    }

    private AiAdminConfigDTO sanitizeForAdmin(AiAdminConfigDTO source) {
        applyApiKeyStatus(source);
        source.getLlm().setApiKey(null);
        return source;
    }

    private void applyApiKeyStatus(AiAdminConfigDTO config) {
        String apiKey = config.getLlm().getApiKey();
        config.getLlm().setApiKeyConfigured(StringUtils.hasText(apiKey));
        config.getLlm().setApiKeyPreview(maskSecret(apiKey));
    }

    private static String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 7) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static int clampInt(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private static double clampDouble(Double value, double fallback, double min, double max) {
        double resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private static Boolean parseBoolean(String value, Boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static Integer parseInt(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Double parseDouble(String value, Double fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String normalizeApiStyle(String value) {
        String normalized = defaultText(value, "chat-completions").toLowerCase();
        return "responses".equals(normalized) ? "responses" : "chat-completions";
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
