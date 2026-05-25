package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.config.AiRuntimeProperties;
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
import com.chen404.service.AiConfigService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.ArticleService;
import com.chen404.service.support.LlmTextStreamHandler;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptBuilder;
import com.chen404.service.support.prompt.AiMaidPromptContext;
import com.chen404.service.support.prompt.AiMaidPromptScene;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import com.chen404.service.support.scenario.chat.MaidChatScenarioDefinition;
import com.chen404.service.support.scenario.chat.MaidChatScenarioRequest;
import com.chen404.service.support.scenario.chat.MaidChatScenarioResult;
import com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioResult;
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

    private static final String DEFAULT_FINISH_REASON = "stop";
    private static final String DEFAULT_MOOD = "happy";
    private static final String SSE_EVENT_SESSION = "session";
    private static final String SSE_EVENT_MESSAGE_START = "message_start";
    private static final String SSE_EVENT_DELTA = "delta";
    private static final String SSE_EVENT_CITATION = "citation";
    private static final String SSE_EVENT_RELATED_ARTICLES = "related_articles";
    private static final String SSE_EVENT_SUGGESTIONS = "suggestions";
    private static final String SSE_EVENT_DONE = "done";
    private static final String SSE_EVENT_ERROR = "error";

    private final AiScenarioExecutor aiScenarioExecutor;
    private final AiRuntimeProperties aiRuntimeProperties;
    private final MaidChatScenarioDefinition maidChatScenarioDefinition;
    private final ArticleService articleService;
    private final ArticleKnowledgeService articleKnowledgeService;
    private final AiMaidPromptBuilder maidPromptBuilder;
    private final AiChatSessionService aiChatSessionService;
    private final AiConfigService aiConfigService;

    public AiChatServiceImpl(
            AiScenarioExecutor aiScenarioExecutor,
            AiRuntimeProperties aiRuntimeProperties,
            MaidChatScenarioDefinition maidChatScenarioDefinition,
            ArticleService articleService,
            ArticleKnowledgeService articleKnowledgeService,
            AiMaidPromptBuilder maidPromptBuilder,
            AiChatSessionService aiChatSessionService,
            AiConfigService aiConfigService) {
        this.aiScenarioExecutor = aiScenarioExecutor;
        this.aiRuntimeProperties = aiRuntimeProperties;
        this.maidChatScenarioDefinition = maidChatScenarioDefinition;
        this.articleService = articleService;
        this.articleKnowledgeService = articleKnowledgeService;
        this.maidPromptBuilder = maidPromptBuilder;
        this.aiChatSessionService = aiChatSessionService;
        this.aiConfigService = aiConfigService;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, Long requesterId) {
        AiAdminConfigDTO effectiveConfig = aiConfigService.getEffectiveConfig();
        ensureChatEnabled(effectiveConfig);
        ensureLlmEnabled(effectiveConfig);
        validateRequest(request);
        ChatExecutionContext context = prepareExecutionContext(request, requesterId, effectiveConfig);
        MaidChatScenarioRequest scenarioRequest = buildScenarioRequest(request, context);
        AiScenarioResult<MaidChatScenarioResult> scenarioExecution = aiScenarioExecutor.execute(
                AiScenarioRequest.of(AiScenarioCode.MAID_CHAT, scenarioRequest)
        );
        AiChatResponse response = buildChatResponse(context, scenarioExecution.data());
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
        AiAdminConfigDTO effectiveConfig = aiConfigService.getEffectiveConfig();
        ChatExecutionContext context = prepareExecutionContext(request, requesterId, effectiveConfig);
        MaidChatScenarioRequest scenarioRequest = buildScenarioRequest(request, context);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });

        STREAM_EXECUTOR.execute(() -> {
            try {
                if (!isChatEnabled(context.aiConfig())) {
                    sendEventQuietly(emitter, SSE_EVENT_ERROR, JSONObject.of("message", "当前环境未开启 AI 聊天能力"));
                    emitter.complete();
                    return;
                }
                if (!isLlmEnabled(context.aiConfig())) {
                    sendEventQuietly(emitter, SSE_EVENT_ERROR, JSONObject.of("message", "LLM is disabled by admin config"));
                    emitter.complete();
                    return;
                }
                emitSessionStart(emitter, context);
                StringBuilder streamedReply = new StringBuilder();
                maidChatScenarioDefinition.stream(
                        scenarioRequest,
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

                AiChatResponse response = buildChatResponse(
                        context,
                        maidChatScenarioDefinition.buildStreamResult(streamedReply.toString(), scenarioRequest)
                );
                aiChatSessionService.saveAssistantMessage(context.session().getSessionId(), response);
                emitRelatedArticles(emitter, response);
                emitSuggestions(emitter, response);
                emitDone(emitter, response);
                emitter.complete();
            } catch (IllegalStateException ex) {
                log.warn("[AI_CHAT_STREAM_FAIL] traceId={} message={}", context.traceId(), ex.getMessage(), ex);
                emitErrorFallback(emitter, context, scenarioRequest, ex.getMessage(), cancelled);
            } catch (Exception ex) {
                log.error("[AI_CHAT_STREAM_ERROR] traceId={} message={}", context.traceId(), ex.getMessage(), ex);
                emitErrorFallback(emitter, context, scenarioRequest, "女仆这次没接稳，请稍后再试。", cancelled);
            }
        });
        return emitter;
    }

    @Override
    public AiChatSessionDetailResponse getSessionDetail(String sessionId, Long requesterId, String visitorId) {
        return aiChatSessionService.loadSessionDetail(sessionId, requesterId, visitorId);
    }

    private ChatExecutionContext prepareExecutionContext(AiChatRequest request, Long requesterId, AiAdminConfigDTO effectiveConfig) {
        String latestUserMessage = extractLatestUserMessage(request.getMessages());
        AiMaidPromptScene scene = resolveScene(request, latestUserMessage);
        String traceId = buildTraceId();
        String messageId = buildMessageId();
        Article currentArticle = loadCurrentArticle(request.getCurrentArticleId(), requesterId, traceId);
        int maxCitationCount = resolveMaxCitationCount(effectiveConfig);
        List<ArticleKnowledgeHit> knowledgeHits = scene == AiMaidPromptScene.HELPER && isRetrievalEnabled(effectiveConfig)
                ? articleKnowledgeService.searchVisibleChunks(latestUserMessage, requesterId, request.getCurrentArticleId(), maxCitationCount)
                : List.of();
        AiMaidPromptContext promptContext = buildPromptContext(request, currentArticle, !knowledgeHits.isEmpty());
        String systemPrompt = maidPromptBuilder.buildSystemPrompt(scene, promptContext, effectiveConfig);
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
        return new ChatExecutionContext(scene, traceId, messageId, requesterId, latestUserMessage, session, currentArticle, knowledgeHits, systemPrompt, effectiveConfig);
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

    private void ensureChatEnabled(AiAdminConfigDTO config) {
        if (!isChatEnabled(config)) {
            throw new IllegalStateException("当前环境未开启 AI 聊天能力");
        }
    }

    private boolean isLlmEnabled(AiAdminConfigDTO config) {
        return config == null || config.getLlm() == null || !Boolean.FALSE.equals(config.getLlm().getEnabled());
    }

    private void ensureLlmEnabled(AiAdminConfigDTO config) {
        if (!isLlmEnabled(config)) {
            throw new IllegalStateException("LLM is disabled by admin config");
        }
    }

    private boolean isChatEnabled(AiAdminConfigDTO config) {
        return config == null || config.getChat() == null || !Boolean.FALSE.equals(config.getChat().getEnabled());
    }

    private boolean isRetrievalEnabled(AiAdminConfigDTO config) {
        return config == null || config.getChat() == null || !Boolean.FALSE.equals(config.getChat().getRetrievalEnabled());
    }

    private int resolveMaxCitationCount(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getMaxCitationCount() != null) {
            return config.getChat().getMaxCitationCount();
        }
        return aiRuntimeProperties.getChat().getMaxCitationCount();
    }

    private int resolveRelatedArticleLimit(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getRelatedArticleLimit() != null) {
            return config.getChat().getRelatedArticleLimit();
        }
        return aiRuntimeProperties.getChat().getRelatedArticleLimit();
    }

    private boolean isRequireRecommendIntent(AiAdminConfigDTO config) {
        if (config != null && config.getChat() != null && config.getChat().getRequireRecommendIntentForRelatedArticles() != null) {
            return config.getChat().getRequireRecommendIntentForRelatedArticles();
        }
        return aiRuntimeProperties.getChat().isRequireRecommendIntentForRelatedArticles();
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

    private MaidChatScenarioRequest buildScenarioRequest(AiChatRequest request, ChatExecutionContext context) {
        return new MaidChatScenarioRequest(
                context.scene(),
                context.systemPrompt(),
                request.getMessages(),
                request.getPageContext(),
                request.getCurrentArticleId(),
                context.currentArticle(),
                context.knowledgeHits(),
                context.aiConfig()
        );
    }

    private AiChatResponse buildChatResponse(ChatExecutionContext context, MaidChatScenarioResult scenarioResult) {
        AiChatResponse response = new AiChatResponse();
        response.setSessionId(context.session().getSessionId());
        response.setMessageId(context.messageId());
        response.setScene(context.scene().name().toLowerCase(Locale.ROOT));
        response.setReplyText(scenarioResult.panelAnswer());
        response.setPanelAnswer(scenarioResult.panelAnswer());
        response.setBubbleText(scenarioResult.bubbleText());
        response.setMood(scenarioResult.mood());
        response.setSuggestions(scenarioResult.suggestions());
        response.setCitations(buildCitations(context.knowledgeHits(), context.aiConfig()));
        response.setRelatedArticles(buildRelatedArticles(context));
        response.setTraceId(context.traceId());
        response.setFinishReason(DEFAULT_FINISH_REASON);
        return response;
    }

    private List<AiChatCitationDTO> buildCitations(List<ArticleKnowledgeHit> knowledgeHits, AiAdminConfigDTO aiConfig) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return List.of();
        }
        List<AiChatCitationDTO> citations = new ArrayList<>();
        int maxCitationCount = resolveMaxCitationCount(aiConfig);
        for (ArticleKnowledgeHit hit : knowledgeHits.stream().limit(maxCitationCount).toList()) {
            AiChatCitationDTO citation = new AiChatCitationDTO();
            citation.setArticleId(hit.articleId());
            citation.setArticleTitle(hit.articleTitle());
            citation.setUrl(hit.url());
            citation.setSnippet(hit.chunkSnippet());
            citations.add(citation);
        }
        return citations;
    }

    private List<AiChatRelatedArticleDTO> buildRelatedArticles(ChatExecutionContext context) {
        if (context.scene() != AiMaidPromptScene.HELPER) {
            return List.of();
        }
        if (!aiRuntimeProperties.getRecommend().isEnabled()) {
            return List.of();
        }
        if (isRequireRecommendIntent(context.aiConfig())
                && !containsRecommendIntent(context.latestUserMessage())) {
            return List.of();
        }
        try {
            AiScenarioResult<ArticleRecommendScenarioResult> scenarioExecution = aiScenarioExecutor.execute(
                    AiScenarioRequest.of(
                        AiScenarioCode.ARTICLE_RECOMMEND,
                        new com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioRequest(
                                context.currentArticle() == null ? null : context.currentArticle().getId(),
                                "article",
                                context.requesterId(),
                                context.latestUserMessage(),
                                resolveRelatedArticleLimit(context.aiConfig())
                        )
                    )
            );
            ArticleRecommendScenarioResult result = scenarioExecution.data();
            if (result == null || result.items() == null || result.items().isEmpty()) {
                return List.of();
            }
            List<AiChatRelatedArticleDTO> relatedArticles = new ArrayList<>();
            for (com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioItem item : result.items()) {
                AiChatRelatedArticleDTO dto = new AiChatRelatedArticleDTO();
                dto.setArticleId(item.articleId());
                dto.setArticleTitle(item.title());
                dto.setUrl(item.url());
                relatedArticles.add(dto);
            }
            return relatedArticles;
        } catch (RuntimeException ex) {
            log.warn("[AI_CHAT_RECOMMEND_SKIP] traceId={} message={}", context.traceId(), ex.getMessage(), ex);
            return List.of();
        }
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
        for (AiChatCitationDTO citation : buildCitations(context.knowledgeHits(), context.aiConfig())) {
            sendEvent(emitter, SSE_EVENT_CITATION, JSON.parseObject(JSON.toJSONString(citation)));
        }
    }

    private void emitSuggestions(SseEmitter emitter, AiChatResponse response) {
        sendEventQuietly(emitter, SSE_EVENT_SUGGESTIONS, JSONObject.of(
                "messageId", response.getMessageId(),
                "items", response.getSuggestions()
        ));
    }

    private void emitRelatedArticles(SseEmitter emitter, AiChatResponse response) {
        if (response.getRelatedArticles() == null || response.getRelatedArticles().isEmpty()) {
            return;
        }
        sendEventQuietly(emitter, SSE_EVENT_RELATED_ARTICLES, JSONObject.of(
                "messageId", response.getMessageId(),
                "items", JSON.parseArray(JSON.toJSONString(response.getRelatedArticles()))
        ));
    }

    private void emitDone(SseEmitter emitter, AiChatResponse response) {
        sendEventQuietly(emitter, SSE_EVENT_DONE, JSONObject.of(
                "messageId", response.getMessageId(),
                "finishReason", response.getFinishReason(),
                "traceId", response.getTraceId(),
                "panelAnswer", response.getPanelAnswer(),
                "bubbleText", response.getBubbleText()
        ));
    }

    private void emitErrorFallback(
            SseEmitter emitter,
            ChatExecutionContext context,
            MaidChatScenarioRequest scenarioRequest,
            String message,
            AtomicBoolean cancelled) {
        if (cancelled.get()) {
            emitter.complete();
            return;
        }
        AiChatResponse fallback = buildChatResponse(context, maidChatScenarioDefinition.buildFallbackResult(scenarioRequest));
        sendEventQuietly(emitter, SSE_EVENT_MESSAGE_START, JSONObject.of(
                "messageId", fallback.getMessageId(),
                "scene", fallback.getScene(),
                "mood", fallback.getMood()
        ));
        sendEventQuietly(emitter, SSE_EVENT_DELTA, buildDeltaPayload(fallback.getMessageId(), fallback.getReplyText()));
        emitRelatedArticles(emitter, fallback);
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

    private boolean containsRecommendIntent(String text) {
        return text.contains("推荐")
                || text.contains("相关")
                || text.contains("看看别的")
                || text.contains("还有什么")
                || text.contains("类似")
                || text.contains("两篇");
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
            Long requesterId,
            String latestUserMessage,
            AiChatSession session,
            Article currentArticle,
            List<ArticleKnowledgeHit> knowledgeHits,
            String systemPrompt,
            AiAdminConfigDTO aiConfig
    ) {
    }
}
