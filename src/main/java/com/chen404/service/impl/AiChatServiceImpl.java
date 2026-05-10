package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.domain.dto.AiChatCitationDTO;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.dto.AiChatRelatedArticleDTO;
import com.chen404.domain.dto.AiChatRequest;
import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.dto.AiChatSessionDetailResponse;
import com.chen404.domain.entity.AiChatSession;
import com.chen404.domain.entity.Article;
import com.chen404.exception.BadRequestException;
import com.chen404.service.AiChatService;
import com.chen404.service.AiChatSessionService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.ArticleService;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.LlmTextStreamHandler;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptBuilder;
import com.chen404.service.support.prompt.AiMaidPromptContext;
import com.chen404.service.support.prompt.AiMaidPromptScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 女仆聊天服务实现。
 * <p>
 * 当前负责：
 * 1. 根据页面与消息判断 helper / companion 场景
 * 2. 组装 Lyra prompt 与知识检索上下文
 * 3. 支持非流式 JSON 回复与 SSE 流式短回复
 * 4. 持久化会话与历史消息
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool();

    private static final int MAX_CONTEXT_MESSAGES = 8;
    private static final int MAX_ARTICLE_CONTENT_CHARS = 3_000;
    private static final int MAX_ARTICLE_SUMMARY_CHARS = 300;
    private static final int MAX_CITATION_COUNT = 3;
    private static final int MAX_SUGGESTION_COUNT = 3;
    private static final String OUTPUT_FIELD_REPLY_TEXT = "replyText";
    private static final String OUTPUT_FIELD_MOOD = "mood";
    private static final String OUTPUT_FIELD_SUGGESTIONS = "suggestions";
    private static final String DEFAULT_MOOD = "happy";
    private static final String DEFAULT_FINISH_REASON = "stop";
    private static final String DEFAULT_REPLY = "我在呢。你可以告诉我你想聊聊，还是想让我帮你看看这页内容呀。";
    private static final String DEFAULT_HELPER_REPLY = "这页如果你想抓重点，我可以先帮你压成几句短的。";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";
    private static final String SSE_EVENT_SESSION = "session";
    private static final String SSE_EVENT_MESSAGE_START = "message_start";
    private static final String SSE_EVENT_DELTA = "delta";
    private static final String SSE_EVENT_CITATION = "citation";
    private static final String SSE_EVENT_SUGGESTIONS = "suggestions";
    private static final String SSE_EVENT_DONE = "done";
    private static final String SSE_EVENT_ERROR = "error";

    private final LlmClient llmClient;
    private final ArticleService articleService;
    private final ArticleKnowledgeService articleKnowledgeService;
    private final AiMaidPromptBuilder maidPromptBuilder;
    private final AiChatSessionService aiChatSessionService;

    public AiChatServiceImpl(
            LlmClient llmClient,
            ArticleService articleService,
            ArticleKnowledgeService articleKnowledgeService,
            AiMaidPromptBuilder maidPromptBuilder,
            AiChatSessionService aiChatSessionService) {
        this.llmClient = llmClient;
        this.articleService = articleService;
        this.articleKnowledgeService = articleKnowledgeService;
        this.maidPromptBuilder = maidPromptBuilder;
        this.aiChatSessionService = aiChatSessionService;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, Long requesterId) {
        validateRequest(request);
        ChatExecutionContext context = prepareExecutionContext(request, requesterId);
        String outputText = llmClient.generateText(LlmTextRequest.of(
                context.systemPrompt(),
                buildStructuredUserPrompt(request, context)
        ));
        AiChatResponse response = parseStructuredResponse(outputText, context, request.getSessionId());
        aiChatSessionService.saveAssistantMessage(context.session().getSessionId(), response);
        log.info("[AI_CHAT_OK] traceId={} requesterId={} scene={} replyLength={} citations={}",
                context.traceId(), requesterId, response.getScene(),
                response.getReplyText() == null ? 0 : response.getReplyText().length(),
                response.getCitations() == null ? 0 : response.getCitations().size());
        return response;
    }

    @Override
    public SseEmitter streamChat(AiChatRequest request, Long requesterId) {
        validateRequest(request);
        ChatExecutionContext context = prepareExecutionContext(request, requesterId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });

        STREAM_EXECUTOR.execute(() -> {
            try {
                emitSessionStart(emitter, context);
                StringBuilder streamedReply = new StringBuilder();
                llmClient.streamText(
                        LlmTextRequest.of(context.systemPrompt(), buildStreamingUserPrompt(request, context)),
                        new LlmTextStreamHandler() {
                            @Override
                            public boolean isCancelled() {
                                return cancelled.get();
                            }

                            @Override
                            public void onTextDelta(String text) {
                                streamedReply.append(text);
                                sendEventQuietly(emitter, SSE_EVENT_DELTA, buildDeltaPayload(context.messageId(), text));
                            }

                            @Override
                            public void onComplete() {
                                // no-op, completion handled after stream returns
                            }
                        }
                );
                if (cancelled.get()) {
                    emitter.complete();
                    return;
                }

                AiChatResponse response = buildStreamResponse(streamedReply.toString(), context);
                aiChatSessionService.saveAssistantMessage(context.session().getSessionId(), response);
                emitSuggestions(emitter, response);
                emitDone(emitter, response);
                emitter.complete();
            } catch (IllegalStateException ex) {
                log.warn("[AI_CHAT_STREAM_FAIL] traceId={} message={}", context.traceId(), ex.getMessage(), ex);
                emitErrorFallback(emitter, context, ex.getMessage(), cancelled);
            } catch (Exception ex) {
                log.error("[AI_CHAT_STREAM_ERROR] traceId={} message={}", context.traceId(), ex.getMessage(), ex);
                emitErrorFallback(emitter, context, "女仆这次没接稳，请稍后再试。", cancelled);
            }
        });
        return emitter;
    }

    @Override
    public AiChatSessionDetailResponse getSessionDetail(String sessionId, Long requesterId, String visitorId) {
        return aiChatSessionService.loadSessionDetail(sessionId, requesterId, visitorId);
    }

    private ChatExecutionContext prepareExecutionContext(AiChatRequest request, Long requesterId) {
        String latestUserMessage = extractLatestUserMessage(request.getMessages());
        AiMaidPromptScene scene = resolveScene(request, latestUserMessage);
        String traceId = buildTraceId();
        String messageId = buildMessageId();
        Article currentArticle = loadCurrentArticle(request.getCurrentArticleId(), requesterId, traceId);
        List<ArticleKnowledgeHit> knowledgeHits = scene == AiMaidPromptScene.HELPER
                ? articleKnowledgeService.searchVisibleChunks(latestUserMessage, requesterId, request.getCurrentArticleId(), MAX_CITATION_COUNT)
                : List.of();
        AiMaidPromptContext promptContext = buildPromptContext(request, currentArticle, !knowledgeHits.isEmpty());
        String systemPrompt = maidPromptBuilder.buildSystemPrompt(scene, promptContext);
        AiChatSession session = aiChatSessionService.ensureSession(
                request.getSessionId(),
                requesterId,
                request.getVisitorId(),
                request.getPageContext(),
                request.getCurrentArticleId(),
                latestUserMessage
        );
        aiChatSessionService.saveUserMessage(session.getSessionId(), latestUserMessage);
        log.info("[AI_CHAT_REQ] traceId={} requesterId={} scene={} pageContext={} articleId={} sessionId={}",
                traceId, requesterId, scene.name().toLowerCase(Locale.ROOT),
                normalizeText(request.getPageContext()), request.getCurrentArticleId(), session.getSessionId());
        return new ChatExecutionContext(scene, traceId, messageId, session, currentArticle, knowledgeHits, systemPrompt);
    }

    private void validateRequest(AiChatRequest request) {
        if (request == null) {
            throw new BadRequestException("聊天请求不能为空");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BadRequestException("聊天消息不能为空");
        }
        String latestUserMessage = extractLatestUserMessage(request.getMessages());
        if (!StringUtils.hasText(latestUserMessage)) {
            throw new BadRequestException("至少需要一条用户消息");
        }
    }

    private AiMaidPromptScene resolveScene(AiChatRequest request, String latestUserMessage) {
        String message = latestUserMessage.toLowerCase(Locale.ROOT);
        if (containsKnowledgeIntent(message)) {
            return AiMaidPromptScene.HELPER;
        }
        if (containsCasualIntent(message)) {
            return AiMaidPromptScene.COMPANION;
        }
        if ("article".equalsIgnoreCase(normalizeText(request.getPageContext()))) {
            return AiMaidPromptScene.HELPER;
        }
        return AiMaidPromptScene.COMPANION;
    }

    private Article loadCurrentArticle(Long articleId, Long requesterId, String traceId) {
        if (articleId == null) {
            return null;
        }
        try {
            return articleService.getArticleById(articleId, false, requesterId);
        } catch (RuntimeException ex) {
            log.warn("[AI_CHAT_ARTICLE_CONTEXT_SKIP] traceId={} articleId={} requesterId={} message={}",
                    traceId, articleId, requesterId, ex.getMessage());
            return null;
        }
    }

    private AiMaidPromptContext buildPromptContext(AiChatRequest request, Article currentArticle, boolean citationsRequired) {
        String articleTitle = currentArticle != null && StringUtils.hasText(currentArticle.getTitle())
                ? currentArticle.getTitle()
                : request.getCurrentArticleTitle();
        return new AiMaidPromptContext(
                normalizeText(request.getPageContext()),
                currentArticle == null ? request.getCurrentArticleId() : currentArticle.getId(),
                normalizeText(articleTitle),
                citationsRequired,
                true
        );
    }

    private String buildStructuredUserPrompt(AiChatRequest request, ChatExecutionContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("请仅返回 JSON 对象，不要输出 markdown、代码块或额外说明。\n");
        builder.append("JSON 字段要求：replyText(string), mood(string), suggestions(string[]).\n");
        builder.append("replyText 必须是简短自然的中文对话；suggestions 最多 3 条。\n\n");
        appendSharedPromptContext(builder, request, context);
        builder.append("### Response requirements\n");
        builder.append("- 回复必须像真实聊天一样短，自然一点。\n");
        builder.append("- 不要解释返回结构，不要附加字段。\n");
        if (context.scene() == AiMaidPromptScene.HELPER) {
            builder.append("- 优先基于当前文章和检索片段给出短回答。\n");
            builder.append("- 如果证据不足，就直接说明当前站内依据还不够。\n");
        } else {
            builder.append("- 先自然接住用户，再给轻快回应。\n");
            builder.append("- 如果适合，可以轻轻引导回页面内容。\n");
        }
        return builder.toString();
    }

    private String buildStreamingUserPrompt(AiChatRequest request, ChatExecutionContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("请直接输出一段简短自然的中文回复，不要 JSON，不要 markdown，不要代码块。\n");
        builder.append("回复长度控制在 1 到 3 句，像日常说话。\n\n");
        appendSharedPromptContext(builder, request, context);
        builder.append("### Response requirements\n");
        builder.append("- 直接给短回复正文。\n");
        builder.append("- 不要输出引用说明，不要列清单。\n");
        if (context.scene() == AiMaidPromptScene.HELPER) {
            builder.append("- 优先围绕当前文章或检索片段回答。\n");
            builder.append("- 没把握就坦白说依据不足。\n");
        } else {
            builder.append("- 保持 Lyra 活泼、可爱，但不要太闹。\n");
        }
        return builder.toString();
    }

    private void appendSharedPromptContext(StringBuilder builder, AiChatRequest request, ChatExecutionContext context) {
        builder.append("### Chat history\n");
        appendRecentMessages(builder, request.getMessages());
        builder.append("\n### Page context\n");
        builder.append("- pageContext: ").append(normalizeText(request.getPageContext())).append('\n');
        builder.append("- currentArticleId: ").append(request.getCurrentArticleId() == null ? "none" : request.getCurrentArticleId()).append('\n');
        if (context.currentArticle() != null) {
            builder.append('\n');
            appendArticleContext(builder, context.currentArticle());
        }
        if (!context.knowledgeHits().isEmpty()) {
            builder.append("\n### Retrieved knowledge\n");
            for (ArticleKnowledgeHit hit : context.knowledgeHits()) {
                builder.append("- [")
                        .append(hit.articleTitle())
                        .append("] ")
                        .append(hit.chunkSnippet())
                        .append('\n');
            }
        }
        builder.append('\n');
    }

    private void appendRecentMessages(StringBuilder builder, List<AiChatMessageDTO> messages) {
        int start = Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES);
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

    private void appendArticleContext(StringBuilder builder, Article article) {
        builder.append("### Current article\n");
        builder.append("Title: ").append(normalizeText(article.getTitle())).append('\n');
        if (StringUtils.hasText(article.getSummary())) {
            builder.append("Summary: ").append(truncate(article.getSummary(), MAX_ARTICLE_SUMMARY_CHARS)).append('\n');
        }
        if (StringUtils.hasText(article.getContent())) {
            builder.append("Content:\n")
                    .append(truncate(article.getContent(), MAX_ARTICLE_CONTENT_CHARS))
                    .append('\n');
        }
    }

    private AiChatResponse parseStructuredResponse(String outputText, ChatExecutionContext context, String sessionId) {
        try {
            JSONObject payload = JSON.parseObject(stripCodeFence(outputText));
            AiChatResponse response = new AiChatResponse();
            response.setSessionId(context.session().getSessionId());
            response.setMessageId(context.messageId());
            response.setScene(context.scene().name().toLowerCase(Locale.ROOT));
            response.setReplyText(resolveReplyText(payload.getString(OUTPUT_FIELD_REPLY_TEXT), context.scene()));
            response.setMood(resolveMood(payload.getString(OUTPUT_FIELD_MOOD)));
            response.setSuggestions(normalizeSuggestions(payload.getJSONArray(OUTPUT_FIELD_SUGGESTIONS), context.scene(), context.currentArticle()));
            response.setCitations(buildCitations(context.knowledgeHits()));
            response.setRelatedArticles(List.of());
            response.setTraceId(context.traceId());
            response.setFinishReason(DEFAULT_FINISH_REASON);
            return response;
        } catch (RuntimeException ex) {
            log.warn("[AI_CHAT_PARSE_FAIL] traceId={} body={}", context.traceId(), outputText, ex);
            return buildFallbackResponse(context);
        }
    }

    private AiChatResponse buildStreamResponse(String streamedReply, ChatExecutionContext context) {
        AiChatResponse response = new AiChatResponse();
        response.setSessionId(context.session().getSessionId());
        response.setMessageId(context.messageId());
        response.setScene(context.scene().name().toLowerCase(Locale.ROOT));
        response.setReplyText(resolveReplyText(streamedReply, context.scene()));
        response.setMood(DEFAULT_MOOD);
        response.setSuggestions(defaultSuggestions(context.scene(), context.currentArticle()));
        response.setCitations(buildCitations(context.knowledgeHits()));
        response.setRelatedArticles(List.of());
        response.setTraceId(context.traceId());
        response.setFinishReason(DEFAULT_FINISH_REASON);
        return response;
    }

    private AiChatResponse buildFallbackResponse(ChatExecutionContext context) {
        AiChatResponse response = new AiChatResponse();
        response.setSessionId(context.session().getSessionId());
        response.setMessageId(context.messageId());
        response.setScene(context.scene().name().toLowerCase(Locale.ROOT));
        response.setReplyText(context.scene() == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY);
        response.setMood(DEFAULT_MOOD);
        response.setSuggestions(defaultSuggestions(context.scene(), context.currentArticle()));
        response.setCitations(buildCitations(context.knowledgeHits()));
        response.setRelatedArticles(List.of());
        response.setTraceId(context.traceId());
        response.setFinishReason(DEFAULT_FINISH_REASON);
        return response;
    }

    private List<AiChatCitationDTO> buildCitations(List<ArticleKnowledgeHit> knowledgeHits) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return List.of();
        }
        List<AiChatCitationDTO> citations = new ArrayList<>();
        for (ArticleKnowledgeHit hit : knowledgeHits.stream().limit(MAX_CITATION_COUNT).toList()) {
            AiChatCitationDTO citation = new AiChatCitationDTO();
            citation.setArticleId(hit.articleId());
            citation.setArticleTitle(hit.articleTitle());
            citation.setUrl(hit.url());
            citation.setSnippet(hit.chunkSnippet());
            citations.add(citation);
        }
        return citations;
    }

    private List<String> normalizeSuggestions(JSONArray rawSuggestions, AiMaidPromptScene scene, Article currentArticle) {
        if (rawSuggestions == null || rawSuggestions.isEmpty()) {
            return defaultSuggestions(scene, currentArticle);
        }
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < rawSuggestions.size() && suggestions.size() < MAX_SUGGESTION_COUNT; i++) {
            String item = rawSuggestions.getString(i);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            suggestions.add(item.trim());
        }
        return suggestions.isEmpty() ? defaultSuggestions(scene, currentArticle) : suggestions;
    }

    private List<String> defaultSuggestions(AiMaidPromptScene scene, Article currentArticle) {
        if (scene == AiMaidPromptScene.HELPER && currentArticle != null) {
            return List.of("帮我总结这篇", "这篇的重点是什么", "推荐两篇相关的");
        }
        if (scene == AiMaidPromptScene.HELPER) {
            return List.of("站内能看什么", "推荐一篇文章", "帮我找找相关内容");
        }
        return List.of("随便陪我聊聊", "给我一句打气的话", "今天适合看什么");
    }

    private void emitSessionStart(SseEmitter emitter, ChatExecutionContext context) {
        sendEvent(emitter, SSE_EVENT_SESSION, JSONObject.of(
                "sessionId", context.session().getSessionId(),
                "scene", context.scene().name().toLowerCase(Locale.ROOT)
        ));
        sendEvent(emitter, SSE_EVENT_MESSAGE_START, JSONObject.of(
                "messageId", context.messageId(),
                "scene", context.scene().name().toLowerCase(Locale.ROOT),
                "mood", DEFAULT_MOOD
        ));
        for (AiChatCitationDTO citation : buildCitations(context.knowledgeHits())) {
            sendEvent(emitter, SSE_EVENT_CITATION, JSON.parseObject(JSON.toJSONString(citation)));
        }
    }

    private void emitSuggestions(SseEmitter emitter, AiChatResponse response) {
        sendEventQuietly(emitter, SSE_EVENT_SUGGESTIONS, JSONObject.of(
                "messageId", response.getMessageId(),
                "items", response.getSuggestions()
        ));
    }

    private void emitDone(SseEmitter emitter, AiChatResponse response) {
        sendEventQuietly(emitter, SSE_EVENT_DONE, JSONObject.of(
                "messageId", response.getMessageId(),
                "finishReason", response.getFinishReason(),
                "traceId", response.getTraceId()
        ));
    }

    private void emitErrorFallback(SseEmitter emitter, ChatExecutionContext context, String message, AtomicBoolean cancelled) {
        if (cancelled.get()) {
            emitter.complete();
            return;
        }
        AiChatResponse fallback = buildFallbackResponse(context);
        sendEventQuietly(emitter, SSE_EVENT_MESSAGE_START, JSONObject.of(
                "messageId", fallback.getMessageId(),
                "scene", fallback.getScene(),
                "mood", fallback.getMood()
        ));
        sendEventQuietly(emitter, SSE_EVENT_DELTA, buildDeltaPayload(fallback.getMessageId(), fallback.getReplyText()));
        emitSuggestions(emitter, fallback);
        emitDone(emitter, fallback);
        aiChatSessionService.saveAssistantMessage(context.session().getSessionId(), fallback);
        sendEventQuietly(emitter, SSE_EVENT_ERROR, JSONObject.of("message", message));
        emitter.complete();
    }

    private JSONObject buildDeltaPayload(String messageId, String text) {
        return JSONObject.of(
                "messageId", messageId,
                "text", text
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, JSONObject payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload.toJSONString()));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 事件发送失败", e);
        }
    }

    private void sendEventQuietly(SseEmitter emitter, String eventName, JSONObject payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload.toJSONString()));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 事件发送失败", e);
        }
    }

    private String extractLatestUserMessage(List<AiChatMessageDTO> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessageDTO message = messages.get(i);
            if ("user".equalsIgnoreCase(normalizeRole(message.getRole())) && StringUtils.hasText(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return "";
    }

    private boolean containsKnowledgeIntent(String text) {
        return text.contains("总结")
                || text.contains("重点")
                || text.contains("这篇")
                || text.contains("文章")
                || text.contains("站内")
                || text.contains("博客")
                || text.contains("推荐")
                || text.contains("内容");
    }

    private boolean containsCasualIntent(String text) {
        return text.contains("你好")
                || text.contains("在吗")
                || text.contains("聊聊")
                || text.contains("陪我")
                || text.contains("有点累")
                || text.contains("打气")
                || text.contains("心情")
                || text.contains("发呆");
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private String stripCodeFence(String text) {
        String trimmed = text == null ? EMPTY_TEXT : text.trim();
        if (trimmed.startsWith(CODE_FENCE_PREFIX)) {
            trimmed = trimmed.replaceFirst(JSON_FENCE_PATTERN, EMPTY_TEXT);
            trimmed = trimmed.replaceFirst(JSON_FENCE_SUFFIX_PATTERN, EMPTY_TEXT);
        }
        return trimmed;
    }

    private String resolveReplyText(String replyText, AiMaidPromptScene scene) {
        if (!StringUtils.hasText(replyText)) {
            return scene == AiMaidPromptScene.HELPER ? DEFAULT_HELPER_REPLY : DEFAULT_REPLY;
        }
        String trimmed = replyText.trim();
        return trimmed.length() > 180 ? trimmed.substring(0, 180) : trimmed;
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

    private String buildMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildTraceId() {
        return "trace_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record ChatExecutionContext(
            AiMaidPromptScene scene,
            String traceId,
            String messageId,
            AiChatSession session,
            Article currentArticle,
            List<ArticleKnowledgeHit> knowledgeHits,
            String systemPrompt
    ) {
    }
}
