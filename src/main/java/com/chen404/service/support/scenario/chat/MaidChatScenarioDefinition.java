package com.chen404.service.support.scenario.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.entity.Article;
import com.chen404.config.AiRuntimeProperties;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.LlmTextStreamHandler;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptScene;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioDefinition;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lyra 聊天场景定义。
 * <p>
 * 负责 prompt 组装、同步返回解析、流式 prompt 组装与 fallback 结果生成。
 */
@Component
public class MaidChatScenarioDefinition implements AiScenarioDefinition<MaidChatScenarioRequest, MaidChatScenarioResult> {

    private static final Logger log = LoggerFactory.getLogger(MaidChatScenarioDefinition.class);

    private static final String OUTPUT_FIELD_REPLY_TEXT = "replyText";
    private static final String OUTPUT_FIELD_PANEL_ANSWER = "panelAnswer";
    private static final String OUTPUT_FIELD_BUBBLE_TEXT = "bubbleText";
    private static final String OUTPUT_FIELD_MOOD = "mood";
    private static final String OUTPUT_FIELD_SUGGESTIONS = "suggestions";
    private static final String DEFAULT_MOOD = "happy";
    private static final String DEFAULT_REPLY = "我在呢。你可以告诉我你想聊聊，还是想让我帮你看看这页内容呀。";
    private static final String DEFAULT_HELPER_REPLY = "这页如果你想抓重点，我可以先帮你压成几句短的。";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";

    private final LlmClient llmClient;
    private final AiRuntimeProperties aiRuntimeProperties;
    private final AiLlmRequestFactory aiLlmRequestFactory;

    public MaidChatScenarioDefinition(
            LlmClient llmClient,
            AiRuntimeProperties aiRuntimeProperties,
            AiLlmRequestFactory aiLlmRequestFactory) {
        this.llmClient = llmClient;
        this.aiRuntimeProperties = aiRuntimeProperties;
        this.aiLlmRequestFactory = aiLlmRequestFactory;
    }

    @Override
    public AiScenarioCode code() {
        return AiScenarioCode.MAID_CHAT;
    }

    @Override
    public AiScenarioResult<MaidChatScenarioResult> execute(AiScenarioRequest<MaidChatScenarioRequest> request) {
        MaidChatScenarioRequest payload = request.payload();
        String outputText = llmClient.generateText(buildLlmRequest(payload, buildStructuredUserPrompt(payload)));
        return AiScenarioResult.of(parseStructuredResponse(outputText, payload));
    }

    public void stream(MaidChatScenarioRequest request, LlmTextStreamHandler handler) {
        llmClient.streamText(
                buildLlmRequest(request, buildStreamingUserPrompt(request)),
                handler
        );
    }

    public MaidChatScenarioResult buildStreamResult(String streamedReply, MaidChatScenarioRequest request) {
        return new MaidChatScenarioResult(
                resolvePanelAnswer(streamedReply, request.scene()),
                resolveBubbleText(streamedReply, request),
                DEFAULT_MOOD,
                defaultSuggestions(request.scene(), request.currentArticle(), request.aiConfig())
        );
    }

    public MaidChatScenarioResult buildFallbackResult(MaidChatScenarioRequest request) {
        return new MaidChatScenarioResult(
                request.scene() == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY,
                request.scene() == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY,
                DEFAULT_MOOD,
                defaultSuggestions(request.scene(), request.currentArticle(), request.aiConfig())
        );
    }

    private MaidChatScenarioResult parseStructuredResponse(String outputText, MaidChatScenarioRequest request) {
        try {
            JSONObject payload = JSON.parseObject(stripCodeFence(outputText));
            String panelAnswer = resolvePanelAnswer(resolvePanelAnswerField(payload), request.scene());
            return new MaidChatScenarioResult(
                    panelAnswer,
                    resolveBubbleText(payload.getString(OUTPUT_FIELD_BUBBLE_TEXT), panelAnswer, request),
                    resolveMood(payload.getString(OUTPUT_FIELD_MOOD)),
                    normalizeSuggestions(payload.getJSONArray(OUTPUT_FIELD_SUGGESTIONS), request.scene(), request.currentArticle(), request.aiConfig())
            );
        } catch (RuntimeException ex) {
            log.warn("[AI_CHAT_PARSE_FAIL] scene={} body={}", request.scene(), outputText, ex);
            return buildFallbackResult(request);
        }
    }

    private String buildStructuredUserPrompt(MaidChatScenarioRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("请仅返回 JSON 对象，不要输出 markdown、代码块或额外说明。\n");
        builder.append("JSON 字段要求：panelAnswer(string), bubbleText(string), mood(string), suggestions(string[]).\n");
        builder.append("panelAnswer 用于聊天面板，可以承载完整回答；bubbleText 用于人物旁小气泡，必须是简短自然的中文短句。\n");
        builder.append("为了兼容旧字段，如果无法返回 panelAnswer，也可以返回 replyText。\n\n");
        appendSharedPromptContext(builder, request);
        builder.append("### Response requirements\n");
        builder.append("- panelAnswer 要自然、清楚，回答用户真正的问题；需要总结或解释时不要为了小气泡而压缩到一句话。\n");
        builder.append("- bubbleText 只负责情绪反馈或引导，不承载完整答案；长回答时使用「").append(resolveLongBubbleText(request)).append("」。\n");
        builder.append("- 不要解释返回结构，不要附加未要求字段。\n");
        if (request.scene() == AiMaidPromptScene.HELPER) {
            builder.append("- 优先基于当前文章和检索片段回答。\n");
            builder.append("- 如果证据不足，就直接说明当前站内依据还不够。\n");
        } else {
            builder.append("- 先自然接住用户，再给轻快回应。\n");
            builder.append("- 如果适合，可以轻轻引导回页面内容。\n");
        }
        return builder.toString();
    }

    private String buildStreamingUserPrompt(MaidChatScenarioRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("请直接输出自然中文回复，不要 JSON，不要 markdown，不要代码块。\n");
        builder.append("这段内容会显示在聊天面板里，可以完整回答用户问题；小气泡会由系统另行压缩。\n\n");
        appendSharedPromptContext(builder, request);
        builder.append("### Response requirements\n");
        builder.append("- 直接给回复正文。\n");
        if (request.scene() == AiMaidPromptScene.HELPER) {
            builder.append("- 优先围绕当前文章或检索片段回答。\n");
            builder.append("- 没把握就坦白说依据不足。\n");
        } else {
            builder.append("- 保持 Lyra 活泼、可爱，但不要太闹。\n");
        }
        return builder.toString();
    }

    private void appendSharedPromptContext(StringBuilder builder, MaidChatScenarioRequest request) {
        builder.append("### Chat history\n");
        appendRecentMessages(builder, request.messages(), request.aiConfig());
        builder.append("\n### Page context\n");
        builder.append("- pageContext: ").append(normalizeText(request.pageContext())).append('\n');
        builder.append("- currentArticleId: ").append(request.currentArticleId() == null ? "none" : request.currentArticleId()).append('\n');
        if (request.currentArticle() != null) {
            builder.append('\n');
            appendArticleContext(builder, request.currentArticle(), request.aiConfig());
        }
        if (request.knowledgeHits() != null && !request.knowledgeHits().isEmpty()) {
            builder.append("\n### Retrieved knowledge\n");
            for (ArticleKnowledgeHit hit : request.knowledgeHits()) {
                builder.append("- [")
                        .append(hit.articleTitle())
                        .append("] ")
                        .append(hit.chunkSnippet())
                        .append('\n');
            }
        }
        builder.append('\n');
    }

    private void appendRecentMessages(StringBuilder builder, List<AiChatMessageDTO> messages, AiAdminConfigDTO aiConfig) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        int start = Math.max(0, messages.size() - resolveMaxContextMessages(aiConfig));
        for (int i = start; i < messages.size(); i++) {
            AiChatMessageDTO message = messages.get(i);
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append("- ")
                    .append(normalizeRole(message.getRole()))
                    .append(": ")
                    .append(message.getContent().trim())
                    .append('\n');
        }
    }

    private void appendArticleContext(StringBuilder builder, Article article, AiAdminConfigDTO aiConfig) {
        builder.append("### Current article\n");
        builder.append("Title: ").append(normalizeText(article.getTitle())).append('\n');
        if (StringUtils.hasText(article.getSummary())) {
            builder.append("Summary: ")
                    .append(truncate(article.getSummary(), resolveMaxArticleSummaryChars(aiConfig)))
                    .append('\n');
        }
        if (StringUtils.hasText(article.getContent())) {
            builder.append("Content:\n")
                    .append(truncate(article.getContent(), resolveMaxArticleContentChars(aiConfig)))
                    .append('\n');
        }
    }

    private List<String> normalizeSuggestions(JSONArray rawSuggestions, AiMaidPromptScene scene, Article currentArticle, AiAdminConfigDTO aiConfig) {
        int maxSuggestionCount = resolveMaxSuggestionCount(aiConfig);
        if (maxSuggestionCount <= 0) {
            return List.of();
        }
        if (rawSuggestions == null || rawSuggestions.isEmpty()) {
            return defaultSuggestions(scene, currentArticle, aiConfig);
        }
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < rawSuggestions.size() && suggestions.size() < maxSuggestionCount; i++) {
            String item = rawSuggestions.getString(i);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            suggestions.add(item.trim());
        }
        return suggestions.isEmpty() ? defaultSuggestions(scene, currentArticle, aiConfig) : suggestions;
    }

    private List<String> defaultSuggestions(AiMaidPromptScene scene, Article currentArticle, AiAdminConfigDTO aiConfig) {
        int maxSuggestionCount = resolveMaxSuggestionCount(aiConfig);
        if (maxSuggestionCount <= 0) {
            return List.of();
        }
        List<String> defaults;
        if (scene == AiMaidPromptScene.HELPER && currentArticle != null) {
            defaults = List.of("帮我总结这篇", "这篇的重点是什么", "推荐两篇相关的");
        } else if (scene == AiMaidPromptScene.HELPER) {
            defaults = List.of("站内能看什么", "推荐一篇文章", "帮我找找相关内容");
        } else {
            defaults = List.of("随便陪我聊聊", "给我一句打气的话", "今天适合看什么");
        }
        return defaults.stream().limit(maxSuggestionCount).toList();
    }

    private LlmTextRequest buildLlmRequest(MaidChatScenarioRequest request, String userPrompt) {
        AiAdminConfigDTO config = request.aiConfig();
        if (config == null || config.getLlm() == null) {
            return aiLlmRequestFactory.buildTextRequest(request.systemPrompt(), userPrompt);
        }
        return aiLlmRequestFactory.buildTextRequest(config, request.systemPrompt(), userPrompt);
    }

    private String resolvePanelAnswerField(JSONObject payload) {
        String panelAnswer = payload.getString(OUTPUT_FIELD_PANEL_ANSWER);
        return StringUtils.hasText(panelAnswer) ? panelAnswer : payload.getString(OUTPUT_FIELD_REPLY_TEXT);
    }

    private String resolveBubbleText(String bubbleText, MaidChatScenarioRequest request) {
        return resolveBubbleText(bubbleText, bubbleText, request);
    }

    private String resolveBubbleText(String bubbleText, String panelAnswer, MaidChatScenarioRequest request) {
        int maxChars = resolveBubbleMaxChars(request);
        String candidate = StringUtils.hasText(bubbleText) ? bubbleText.trim() : panelAnswer;
        if (!StringUtils.hasText(candidate)) {
            return request.scene() == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY;
        }
        String normalized = candidate.trim();
        if (normalized.length() > maxChars || normalized.contains("\n")) {
            return resolveLongBubbleText(request);
        }
        return normalized;
    }

    private int resolveBubbleMaxChars(MaidChatScenarioRequest request) {
        if (request.aiConfig() != null
                && request.aiConfig().getChat() != null
                && request.aiConfig().getChat().getBubbleMaxChars() != null) {
            return request.aiConfig().getChat().getBubbleMaxChars();
        }
        return 36;
    }

    private String resolveLongBubbleText(MaidChatScenarioRequest request) {
        if (request.aiConfig() != null
                && request.aiConfig().getChat() != null
                && StringUtils.hasText(request.aiConfig().getChat().getBubbleLongReplyText())) {
            return request.aiConfig().getChat().getBubbleLongReplyText();
        }
        return "我整理好了，打开聊天框看详细内容吧。";
    }

    private int resolveMaxContextMessages(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getMaxContextMessages() != null) {
            return config.getChat().getMaxContextMessages();
        }
        return aiRuntimeProperties.getChat().getMaxContextMessages();
    }

    private int resolveMaxSuggestionCount(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getMaxSuggestionCount() != null) {
            return config.getChat().getMaxSuggestionCount();
        }
        return aiRuntimeProperties.getChat().getMaxSuggestionCount();
    }

    private int resolveMaxArticleSummaryChars(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getMaxArticleSummaryChars() != null) {
            return config.getChat().getMaxArticleSummaryChars();
        }
        return aiRuntimeProperties.getChat().getMaxArticleSummaryChars();
    }

    private int resolveMaxArticleContentChars(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getMaxArticleContentChars() != null) {
            return config.getChat().getMaxArticleContentChars();
        }
        return aiRuntimeProperties.getChat().getMaxArticleContentChars();
    }

    private String stripCodeFence(String text) {
        String trimmed = text == null ? EMPTY_TEXT : text.trim();
        if (trimmed.startsWith(CODE_FENCE_PREFIX)) {
            trimmed = trimmed.replaceFirst(JSON_FENCE_PATTERN, EMPTY_TEXT);
            trimmed = trimmed.replaceFirst(JSON_FENCE_SUFFIX_PATTERN, EMPTY_TEXT);
        }
        return trimmed;
    }

    private String resolvePanelAnswer(String replyText, AiMaidPromptScene scene) {
        if (!StringUtils.hasText(replyText)) {
            return scene == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY;
        }
        return replyText.trim();
    }

    private String resolveMood(String mood) {
        if (!StringUtils.hasText(mood)) {
            return DEFAULT_MOOD;
        }
        return mood.trim();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalizeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "unknown";
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }
}
