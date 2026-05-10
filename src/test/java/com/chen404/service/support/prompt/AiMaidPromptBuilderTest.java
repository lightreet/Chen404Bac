package com.chen404.service.support.prompt;

import com.chen404.config.AiMaidProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 女仆 prompt 构建器测试。
 */
class AiMaidPromptBuilderTest {

    @Test
    void shouldBuildHelperPromptWithRuntimeContext() {
        AiMaidPromptBuilder builder = createBuilder(new AiMaidProperties());

        String prompt = builder.buildSystemPrompt(
                AiMaidPromptScene.HELPER,
                new AiMaidPromptContext("article", 404L, "Live2D 接入 AI 方案", true, true)
        );

        assertTrue(prompt.contains("你叫Lyra"));
        assertTrue(prompt.contains("Task mode: helper"));
        assertTrue(prompt.contains("pageContext: article"));
        assertTrue(prompt.contains("currentArticleId: 404"));
        assertTrue(prompt.contains("currentArticleTitle: Live2D 接入 AI 方案"));
        assertTrue(prompt.contains("citationsRequired: true"));
    }

    @Test
    void shouldBuildCompanionPromptWithSceneSpecificInstruction() {
        AiMaidPromptBuilder builder = createBuilder(new AiMaidProperties());

        String prompt = builder.buildSystemPrompt(
                AiMaidPromptScene.COMPANION,
                new AiMaidPromptContext("home", null, "", false, true)
        );

        assertTrue(prompt.contains("Task mode: companion"));
        assertTrue(prompt.contains("allowCasualConversation: true"));
        assertTrue(prompt.contains("轻量陪伴聊天模式"));
    }

    @Test
    void shouldThrowWhenPromptTemplateMissing() {
        AiMaidProperties properties = new AiMaidProperties();
        properties.setSystemPromptLocation("classpath:prompts/ai/not-exists.txt");

        AiMaidPromptBuilder builder = createBuilder(properties);

        assertThrows(IllegalStateException.class,
                () -> builder.buildSystemPrompt(AiMaidPromptScene.HELPER, AiMaidPromptContext.empty()));
    }

    private AiMaidPromptBuilder createBuilder(AiMaidProperties properties) {
        AiPromptTemplateLoader loader = new AiPromptTemplateLoader(new DefaultResourceLoader());
        return new AiMaidPromptBuilder(properties, loader);
    }
}
