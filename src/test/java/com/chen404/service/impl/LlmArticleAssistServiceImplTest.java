package com.chen404.service.impl;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.service.AiConfigService;
import com.chen404.service.FeatureToggleService;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.article.ArticleAssistScenarioDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmArticleAssistServiceImplTest {

    @Test
    void generateAssistShouldRejectBeforeScenarioExecutionWhenFeatureIsDisabled() {
        AiScenarioExecutor executor = mock(AiScenarioExecutor.class);
        LlmArticleAssistServiceImpl service = new LlmArticleAssistServiceImpl(
                executor,
                mock(FeatureToggleService.class)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.generateAssist(new AiArticleAssistRequest())
        );

        assertEquals("AI 文章助手当前已在管理后台关闭", exception.getMessage());
        verifyNoInteractions(executor);
    }

    @Test
    void generateAssistShouldReuseLlmClientAndNormalizeJsonResult() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                ```json
                {
                  "summary": "这是一篇关于 Spring Boot 接入 LLM 的文章摘要。",
                  "tags": ["Spring Boot", "LLM", "OpenAI", "LLM", "后端开发", "额外标签"]
                }
                ```
                """);

        AiRuntimeProperties aiRuntimeProperties = new AiRuntimeProperties();
        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new ArticleAssistScenarioDefinition(llmClient, aiRuntimeProperties, requestFactory())));
        LlmArticleAssistServiceImpl service = new LlmArticleAssistServiceImpl(executor, enabledFeatures());
        AiArticleAssistRequest request = new AiArticleAssistRequest();
        request.setTitle("Spring Boot 接入大模型");
        request.setContent("# 标题\n这是一段用于测试的正文内容。");

        AiArticleAssistResponse response = service.generateAssist(request);

        assertEquals("这是一篇关于 Spring Boot 接入 LLM 的文章摘要。", response.getSummary());
        assertIterableEquals(List.of("Spring Boot", "LLM", "OpenAI", "后端开发", "额外标签"), response.getTags());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        LlmTextRequest llmRequest = requestCaptor.getValue();
        assertEquals("You are an assistant for a Chinese technical blog CMS. Return valid JSON only.", llmRequest.systemInstruction());
        assertEquals("https://db.example/v1", llmRequest.baseUrl());
        assertEquals("gpt-db", llmRequest.model());
        assertEquals("sk-db", llmRequest.apiKey());
        assertTrue(llmRequest.userPrompt().contains("Spring Boot 接入大模型"));
        assertTrue(llmRequest.userPrompt().contains("这是一段用于测试的正文内容。"));
        assertTrue(llmRequest.userPrompt().contains("prefer exactly 3 concise Chinese tags"));
        assertTrue(llmRequest.userPrompt().contains("sorted by relevance from highest to lowest"));
    }

    @Test
    void generateAssistShouldAddRegenerateConstraintsWhenRequested() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {
                  "summary": "这是一个新的替代摘要。",
                  "tags": ["Spring Boot", "LLM", "标签系统"]
                }
                """);

        AiRuntimeProperties aiRuntimeProperties = new AiRuntimeProperties();
        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new ArticleAssistScenarioDefinition(llmClient, aiRuntimeProperties, requestFactory())));
        LlmArticleAssistServiceImpl service = new LlmArticleAssistServiceImpl(executor, enabledFeatures());
        AiArticleAssistRequest request = new AiArticleAssistRequest();
        request.setTitle("Spring Boot 接入大模型");
        request.setContent("正文内容");
        request.setRegenerate(true);
        request.setCurrentSummary("这是旧摘要");
        request.setCurrentTags(List.of("旧标签", "Spring Boot"));

        service.generateAssist(request);

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
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl("https://db.example/v1");
        config.getLlm().setModel("gpt-db");
        config.getLlm().setApiKey("sk-db");
        config.getLlm().setApiStyle("chat-completions");
        config.getLlm().setTemperature(0.2);
        config.getLlm().setMaxTokens(512);
        config.getLlm().setTimeoutSeconds(30);
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(config);
        return new AiLlmRequestFactory(aiConfigService);
    }

    private FeatureToggleService enabledFeatures() {
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isEnabled(RuntimeFeatureEnum.AI_ARTICLE_ASSIST)).thenReturn(true);
        return featureToggleService;
    }
}
