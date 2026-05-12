package com.chen404.service.impl;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.dto.AiChatRequest;
import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.entity.AiChatSession;
import com.chen404.domain.entity.Article;
import com.chen404.service.AiChatSessionService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.ArticleService;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptBuilder;
import com.chen404.service.support.prompt.AiMaidPromptContext;
import com.chen404.service.support.prompt.AiMaidPromptScene;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioDefinition;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.chat.MaidChatScenarioDefinition;
import com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioItem;
import com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioRequest;
import com.chen404.service.support.scenario.recommend.ArticleRecommendScenarioResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private LlmClient llmClient;
    private ArticleService articleService;
    private ArticleKnowledgeService articleKnowledgeService;
    private AiMaidPromptBuilder promptBuilder;
    private AiChatSessionService aiChatSessionService;
    private AiChatServiceImpl aiChatService;
    private StubRecommendScenarioDefinition recommendScenarioDefinition;
    private AiRuntimeProperties aiRuntimeProperties;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        articleService = mock(ArticleService.class);
        articleKnowledgeService = mock(ArticleKnowledgeService.class);
        promptBuilder = mock(AiMaidPromptBuilder.class);
        aiChatSessionService = mock(AiChatSessionService.class);
        aiRuntimeProperties = new AiRuntimeProperties();
        MaidChatScenarioDefinition scenarioDefinition = new MaidChatScenarioDefinition(llmClient, aiRuntimeProperties);
        recommendScenarioDefinition = new StubRecommendScenarioDefinition();
        AiScenarioExecutor aiScenarioExecutor = new AiScenarioExecutor(List.of(scenarioDefinition, recommendScenarioDefinition));
        aiChatService = new AiChatServiceImpl(
                aiScenarioExecutor,
                aiRuntimeProperties,
                scenarioDefinition,
                articleService,
                articleKnowledgeService,
                promptBuilder,
                aiChatSessionService
        );

        when(promptBuilder.buildSystemPrompt(any(AiMaidPromptScene.class), any(AiMaidPromptContext.class)))
                .thenReturn("system prompt");
        when(aiChatSessionService.ensureSession(nullable(String.class), any(), nullable(String.class), anyString(), any(), anyString()))
                .thenReturn(buildSession());
    }

    @Test
    void shouldBuildHelperResponseWithoutRelatedArticlesWhenUserDidNotAskForRecommendation() {
        Article article = new Article();
        article.setId(123L);
        article.setTitle("智能女仆接入方案");
        article.setSummary("第一阶段围绕当前文章和站内知识给出可靠的短回答。");
        article.setContent("这里是更长一些的文章正文，用来给 Lyra 补充当前文章上下文。");
        when(articleService.getArticleById(123L, false, 7L)).thenReturn(article);
        when(articleKnowledgeService.searchVisibleChunks(eq("帮我总结一下这篇文章"), eq(7L), eq(123L), anyInt()))
                .thenReturn(List.of(new ArticleKnowledgeHit(
                        123L,
                        "智能女仆接入方案",
                        "第一阶段先把聊天入口、短回复和站内知识引导做好。",
                        "/articles/123",
                        95,
                        true
                )));
        when(llmClient.generateText(any(LlmTextRequest.class)))
                .thenReturn("{\"replyText\":\"这篇主要在讲女仆聊天接入思路，我可以继续帮你压成三条重点。\",\"mood\":\"happy\",\"suggestions\":[\"帮我总结这篇\"]}");

        AiChatResponse response = aiChatService.chat(buildArticleHelperRequest(), 7L);

        assertEquals("helper", response.getScene());
        assertTrue(response.getReplyText().contains("接入思路"));
        assertEquals("happy", response.getMood());
        assertEquals(1, response.getCitations().size());
        assertEquals(123L, response.getCitations().get(0).getArticleId());
        assertEquals("/articles/123", response.getCitations().get(0).getUrl());
        assertTrue(response.getRelatedArticles().isEmpty());
        assertFalse(response.getSuggestions().isEmpty());
        assertEquals("sess_test", response.getSessionId());
        verify(aiChatSessionService).saveUserMessage("sess_test", "帮我总结一下这篇文章");
        verify(aiChatSessionService).saveAssistantMessage(eq("sess_test"), any(AiChatResponse.class));
    }

    @Test
    void shouldAttachRelatedArticlesWhenUserExpressesRecommendIntent() {
        Article article = new Article();
        article.setId(123L);
        article.setTitle("智能女仆接入方案");
        when(articleService.getArticleById(123L, false, 7L)).thenReturn(article);
        when(articleKnowledgeService.searchVisibleChunks(eq("推荐两篇相关的文章"), eq(7L), eq(123L), anyInt()))
                .thenReturn(List.of());
        when(llmClient.generateText(any(LlmTextRequest.class)))
                .thenReturn("{\"replyText\":\"我给你挑了两篇站内相关内容。\",\"mood\":\"happy\",\"suggestions\":[\"打开第一篇看看\"]}");

        AiChatResponse response = aiChatService.chat(buildRecommendRequest(), 7L);

        assertEquals(1, response.getRelatedArticles().size());
        assertEquals(88L, response.getRelatedArticles().get(0).getArticleId());
    }

    @Test
    void shouldFallbackWhenLlmReturnsInvalidJson() {
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("not-json");

        AiChatResponse response = aiChatService.chat(buildCompanionRequest(), null);

        assertEquals("companion", response.getScene());
        assertNotNull(response.getReplyText());
        assertFalse(response.getReplyText().isBlank());
        assertFalse(response.getSuggestions().isEmpty());
        assertTrue(response.getCitations().isEmpty());
        verify(articleKnowledgeService, never()).searchVisibleChunks(anyString(), any(), any(), anyInt());
    }

    @Test
    void shouldSendRecentMessagesIntoPrompt() {
        when(llmClient.generateText(any(LlmTextRequest.class)))
                .thenReturn("{\"replyText\":\"我在呢。\",\"mood\":\"playful\",\"suggestions\":[\"随便陪我聊聊\"]}");

        aiChatService.chat(buildCompanionRequest(), null);

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().userPrompt().contains("今天有点累，陪我聊聊吧"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("### Chat history"));
    }

    private AiChatRequest buildArticleHelperRequest() {
        AiChatRequest request = new AiChatRequest();
        request.setPageContext("article");
        request.setCurrentArticleId(123L);
        request.setMessages(List.of(message("user", "帮我总结一下这篇文章")));
        return request;
    }

    private AiChatRequest buildCompanionRequest() {
        AiChatRequest request = new AiChatRequest();
        request.setPageContext("home");
        request.setMessages(List.of(message("user", "今天有点累，陪我聊聊吧")));
        return request;
    }

    private AiChatRequest buildRecommendRequest() {
        AiChatRequest request = new AiChatRequest();
        request.setPageContext("article");
        request.setCurrentArticleId(123L);
        request.setMessages(List.of(message("user", "推荐两篇相关的文章")));
        return request;
    }

    private AiChatMessageDTO message(String role, String content) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AiChatSession buildSession() {
        AiChatSession session = new AiChatSession();
        session.setSessionId("sess_test");
        return session;
    }

    private static final class StubRecommendScenarioDefinition implements AiScenarioDefinition<ArticleRecommendScenarioRequest, ArticleRecommendScenarioResult> {

        @Override
        public AiScenarioCode code() {
            return AiScenarioCode.ARTICLE_RECOMMEND;
        }

        @Override
        public AiScenarioResult<ArticleRecommendScenarioResult> execute(AiScenarioRequest<ArticleRecommendScenarioRequest> request) {
            return AiScenarioResult.of(new ArticleRecommendScenarioResult(
                    List.of(new ArticleRecommendScenarioItem(88L, "相关文章", "共享标签", "/article/88")),
                    "规则召回已返回站内相关文章。",
                    "trace_recommend",
                    "rule"
            ));
        }
    }
}
