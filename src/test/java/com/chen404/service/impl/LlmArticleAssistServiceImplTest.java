package com.chen404.service.impl;

import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.article.ArticleAssistScenarioDefinition;
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

class LlmArticleAssistServiceImplTest {

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

        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new ArticleAssistScenarioDefinition(llmClient)));
        LlmArticleAssistServiceImpl service = new LlmArticleAssistServiceImpl(executor);
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

        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new ArticleAssistScenarioDefinition(llmClient)));
        LlmArticleAssistServiceImpl service = new LlmArticleAssistServiceImpl(executor);
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
}
