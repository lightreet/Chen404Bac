package com.chen404.service.support.scenario.chat;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.entity.Article;
import com.chen404.service.AiConfigService;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptScene;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaidChatScenarioDefinitionTest {

    @Test
    void shouldBuildStructuredPromptAndParseStructuredReply() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {"panelAnswer":"This is a longer panel answer for the chat panel.","bubbleText":"Done.","mood":"happy","suggestions":["Summarize this article"]}
                """);

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.HELPER,
                        "system prompt",
                        List.of(message("user", "Please summarize this article")),
                        "article",
                        123L,
                        buildArticle(),
                        List.of(new ArticleKnowledgeHit(123L, "AI maid plan", "The first section explains chat entry and site retrieval.", "/articles/123", 95, true)),
                        defaultAiConfig()
                )
        )).data();

        assertEquals("This is a longer panel answer for the chat panel.", result.panelAnswer());
        assertEquals("Done.", result.bubbleText());
        assertEquals("happy", result.mood());
        assertEquals(List.of("Summarize this article"), result.suggestions());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertEquals("system prompt", requestCaptor.getValue().systemInstruction());
        assertEquals("gpt-test", requestCaptor.getValue().model());
        assertEquals("https://llm.example/v1", requestCaptor.getValue().baseUrl());
        assertTrue(requestCaptor.getValue().userPrompt().contains("### Chat history"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("### Retrieved knowledge"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("AI maid plan"));
    }

    @Test
    void shouldKeepLongAnswerInPanelAndUseShortBubbleFallback() {
        LlmClient llmClient = mock(LlmClient.class);
        String longPanelAnswer = "This panel answer is intentionally longer than one hundred and eighty characters. "
                + "It should stay complete because the chat panel is allowed to carry the detailed explanation, "
                + "while only the small Live2D bubble should be shortened for visual comfort.";
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {"panelAnswer":"%s","mood":"happy","suggestions":[]}
                """.formatted(longPanelAnswer));

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.HELPER,
                        "system prompt",
                        List.of(message("user", "Please explain it")),
                        "article",
                        123L,
                        buildArticle(),
                        List.of(),
                        defaultAiConfig()
                )
        )).data();

        assertEquals(longPanelAnswer, result.panelAnswer());
        assertEquals("我整理好了，打开聊天框看详细内容吧。", result.bubbleText());
    }

    @Test
    void shouldReturnNoSuggestionsWhenAdminLimitIsZero() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {"panelAnswer":"ok","bubbleText":"ok","mood":"happy","suggestions":["one","two"]}
                """);
        AiAdminConfigDTO config = defaultAiConfig();
        config.getChat().setMaxSuggestionCount(0);

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.COMPANION,
                        "system prompt",
                        List.of(message("user", "Please chat")),
                        "home",
                        null,
                        null,
                        List.of(),
                        config
                )
        )).data();

        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void shouldFallbackWhenStructuredReplyIsInvalid() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("not-json");

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.COMPANION,
                        "system prompt",
                        List.of(message("user", "I feel tired today")),
                        "home",
                        null,
                        null,
                        List.of(),
                        defaultAiConfig()
                )
        )).data();

        assertFalse(result.panelAnswer().isBlank());
        assertFalse(result.bubbleText().isBlank());
        assertEquals("happy", result.mood());
        assertFalse(result.suggestions().isEmpty());
    }

    @Test
    void shouldUseAdminContextAndSuggestionLimits() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {"panelAnswer":"ok","bubbleText":"ok","mood":"happy","suggestions":["one","two","three"]}
                """);
        AiAdminConfigDTO config = defaultAiConfig();
        config.getChat().setMaxContextMessages(1);
        config.getChat().setMaxSuggestionCount(1);

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.COMPANION,
                        "system prompt",
                        List.of(message("user", "older message"), message("user", "newer message")),
                        "home",
                        null,
                        null,
                        List.of(),
                        config
                )
        )).data();

        assertEquals(List.of("one"), result.suggestions());
        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertFalse(requestCaptor.getValue().userPrompt().contains("older message"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("newer message"));
    }

    private AiChatMessageDTO message(String role, String content) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private Article buildArticle() {
        Article article = new Article();
        article.setId(123L);
        article.setTitle("AI maid plan");
        article.setSummary("A concise summary about the AI maid assistant.");
        article.setContent("Longer article body that gives Lyra enough page context.");
        return article;
    }

    private AiLlmRequestFactory requestFactory() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(defaultAiConfig());
        return new AiLlmRequestFactory(aiConfigService);
    }

    private AiAdminConfigDTO defaultAiConfig() {
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl("https://llm.example/v1");
        config.getLlm().setModel("gpt-test");
        config.getLlm().setApiKey("sk-test");
        config.getLlm().setApiStyle("chat-completions");
        config.getLlm().setTemperature(0.2);
        config.getLlm().setMaxTokens(512);
        config.getLlm().setTimeoutSeconds(30);
        config.getChat().setBubbleMaxChars(10);
        config.getChat().setBubbleLongReplyText("我整理好了，打开聊天框看详细内容吧。");
        config.getChat().setMaxArticleContentChars(3000);
        config.getChat().setMaxArticleSummaryChars(360);
        return config;
    }
}
