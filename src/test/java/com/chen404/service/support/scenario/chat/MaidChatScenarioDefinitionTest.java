package com.chen404.service.support.scenario.chat;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.entity.Article;
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
                {"replyText":"这篇主要在讲女仆聊天接入思路，我可以继续帮你压成三条重点。","mood":"happy","suggestions":["帮我总结这篇"]}
                """);

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(llmClient, new AiRuntimeProperties());
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.HELPER,
                        "system prompt",
                        List.of(message("user", "帮我总结一下这篇文章")),
                        "article",
                        123L,
                        buildArticle(),
                        List.of(new ArticleKnowledgeHit(123L, "智能女仆接入方案", "第一阶段先把聊天入口、短回复和站内知识引导做好。", "/articles/123", 95, true))
                )
        )).data();

        assertTrue(result.replyText().contains("接入思路"));
        assertEquals("happy", result.mood());
        assertEquals(List.of("帮我总结这篇"), result.suggestions());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertEquals("system prompt", requestCaptor.getValue().systemInstruction());
        assertTrue(requestCaptor.getValue().userPrompt().contains("### Chat history"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("### Retrieved knowledge"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("智能女仆接入方案"));
    }

    @Test
    void shouldFallbackWhenStructuredReplyIsInvalid() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("not-json");

        MaidChatScenarioDefinition definition = new MaidChatScenarioDefinition(llmClient, new AiRuntimeProperties());
        MaidChatScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.MAID_CHAT,
                new MaidChatScenarioRequest(
                        AiMaidPromptScene.COMPANION,
                        "system prompt",
                        List.of(message("user", "今天有点累，陪我聊聊吧")),
                        "home",
                        null,
                        null,
                        List.of()
                )
        )).data();

        assertFalse(result.replyText().isBlank());
        assertEquals("happy", result.mood());
        assertFalse(result.suggestions().isEmpty());
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
        article.setTitle("智能女仆接入方案");
        article.setSummary("第一阶段围绕当前文章和站内知识给出可靠的短回答。");
        article.setContent("这里是更长一些的文章正文，用来给 Lyra 补充当前文章上下文。");
        return article;
    }
}
