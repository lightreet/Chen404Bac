package com.chen404.service.support.prompt;

import com.chen404.config.AiMaidProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 女仆 prompt 构建器。
 * <p>
 * 负责组装角色系统 prompt、场景任务 prompt 与运行时上下文，
 * 为后续 AiChatService 提供稳定、可测试的系统提示词入口。
 */
@Component
public class AiMaidPromptBuilder {

    private static final String PLACEHOLDER_MAID_NAME = "{{maidName}}";
    private static final String PLACEHOLDER_PERSONA_VERSION = "{{personaVersion}}";
    private static final String UNKNOWN_VALUE = "unknown";
    private static final String NONE_VALUE = "none";
    private static final String LINE_SEPARATOR = "\n";

    private final AiMaidProperties maidProperties;
    private final AiPromptTemplateLoader promptTemplateLoader;

    public AiMaidPromptBuilder(AiMaidProperties maidProperties, AiPromptTemplateLoader promptTemplateLoader) {
        this.maidProperties = maidProperties;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 构建指定场景的系统 prompt。
     *
     * @param scene   prompt 场景
     * @param context 运行时上下文
     * @return 已渲染并拼装完成的系统 prompt
     */
    public String buildSystemPrompt(AiMaidPromptScene scene, AiMaidPromptContext context) {
        AiMaidPromptContext safeContext = context == null ? AiMaidPromptContext.empty() : context;
        String systemPrompt = renderPersonaTemplate(
                promptTemplateLoader.loadRequiredTemplate(maidProperties.getSystemPromptLocation())
        );
        String taskPrompt = promptTemplateLoader.loadRequiredTemplate(scene.resolveTaskPromptLocation(maidProperties));
        return String.join(LINE_SEPARATOR + LINE_SEPARATOR,
                systemPrompt,
                taskPrompt,
                buildRuntimeContextBlock(scene, safeContext)
        );
    }

    private String renderPersonaTemplate(String template) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PLACEHOLDER_MAID_NAME, normalizeText(maidProperties.getName(), "Lyra"));
        values.put(PLACEHOLDER_PERSONA_VERSION, normalizeText(maidProperties.getPersonaVersion(), "v1.1"));

        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        return rendered;
    }

    private String buildRuntimeContextBlock(AiMaidPromptScene scene, AiMaidPromptContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("### Runtime context").append(LINE_SEPARATOR);
        builder.append("- scene: ").append(scene.name().toLowerCase()).append(LINE_SEPARATOR);
        builder.append("- maidName: ").append(normalizeText(maidProperties.getName(), "Lyra")).append(LINE_SEPARATOR);
        builder.append("- personaVersion: ").append(normalizeText(maidProperties.getPersonaVersion(), "v1.1")).append(LINE_SEPARATOR);
        builder.append("- pageContext: ").append(normalizeText(context.pageContext(), UNKNOWN_VALUE)).append(LINE_SEPARATOR);
        builder.append("- currentArticleId: ").append(context.currentArticleId() == null ? NONE_VALUE : context.currentArticleId()).append(LINE_SEPARATOR);
        builder.append("- currentArticleTitle: ").append(normalizeText(context.currentArticleTitle(), NONE_VALUE)).append(LINE_SEPARATOR);
        builder.append("- citationsRequired: ").append(context.citationsRequired()).append(LINE_SEPARATOR);
        builder.append("- allowCasualConversation: ").append(context.allowCasualConversation()).append(LINE_SEPARATOR);
        builder.append("- instruction: 回答时必须服从以上运行时边界；若上下文缺失，不要自行脑补站内事实。");
        return builder.toString();
    }

    private String normalizeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
