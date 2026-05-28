package com.chen404.service.support.scenario.article;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.service.AiConfigService;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleAssistScenarioDefinitionTest {

    @Test
    void shouldBuildPromptAndParseNormalizedResult() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                ```json
                {
                  "summary": "这是一篇关于 Spring Boot 接入 LLM 的文章摘要。",
                  "tags": ["Spring Boot", "LLM", "OpenAI", "LLM", "后端开发", "额外标签"]
                }
                ```
                """);

        ArticleAssistScenarioDefinition definition = new ArticleAssistScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        ArticleAssistScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.ARTICLE_ASSIST,
                        new ArticleAssistScenarioRequest(
                                "Spring Boot 接入大模型",
                                "# 标题\n这是一段用于测试的正文内容。",
                                false,
                                null,
                                null
                        )
                )
        ).data();

        assertEquals("这是一篇关于 Spring Boot 接入 LLM 的文章摘要。", result.summary());
        assertIterableEquals(List.of("Spring Boot", "LLM", "OpenAI", "后端开发", "额外标签"), result.tags());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertEquals("You are an assistant for a Chinese technical blog CMS. Return valid JSON only.",
                requestCaptor.getValue().systemInstruction());
        assertTrue(requestCaptor.getValue().userPrompt().contains("Spring Boot 接入大模型"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("这是一段用于测试的正文内容。"));
    }

    @Test
    void shouldIncludeRegenerateConstraintsWhenRequested() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {
                  "summary": "这是一个新的替代摘要。",
                  "tags": ["Spring Boot", "LLM", "标签系统"]
                }
                """);

        ArticleAssistScenarioDefinition definition = new ArticleAssistScenarioDefinition(
                llmClient,
                new AiRuntimeProperties(),
                requestFactory()
        );
        definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.ARTICLE_ASSIST,
                        new ArticleAssistScenarioRequest(
                                "Spring Boot 接入大模型",
                                "正文内容",
                                true,
                                "这是旧摘要",
                                List.of("旧标签", "Spring Boot")
                        )
                )
        );

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        String prompt = requestCaptor.getValue().userPrompt();
        assertTrue(prompt.contains("This is a regeneration request"));
        assertTrue(prompt.contains("Current summary to avoid repeating"));
        assertTrue(prompt.contains("这是旧摘要"));
        assertTrue(prompt.contains("Current tags to avoid repeating exactly"));
        assertTrue(prompt.contains("旧标签, Spring Boot"));
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
        return config;
    }
}
