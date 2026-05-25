package com.chen404.service.support.prompt;

import com.chen404.config.AiMaidProperties;
import com.chen404.domain.dto.AiAdminConfigDTO;
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
        return buildSystemPrompt(scene, context, null);
    }

    public String buildSystemPrompt(AiMaidPromptScene scene, AiMaidPromptContext context, AiAdminConfigDTO aiConfig) {
        AiMaidPromptContext safeContext = context == null ? AiMaidPromptContext.empty() : context;
        AiAdminConfigDTO.MaidConfig maidConfig = aiConfig == null ? null : aiConfig.getMaid();
        String systemPrompt = renderPersonaTemplate(
                resolveSystemPromptTemplate(maidConfig),
                maidConfig
        );
        String taskPrompt = resolveTaskPromptTemplate(scene, maidConfig);
        return String.join(LINE_SEPARATOR + LINE_SEPARATOR,
                systemPrompt,
                taskPrompt,
                buildRuntimeContextBlock(scene, safeContext, maidConfig)
        );
    }

    private String resolveSystemPromptTemplate(AiAdminConfigDTO.MaidConfig maidConfig) {
        if (maidConfig != null && StringUtils.hasText(maidConfig.getSystemPrompt())) {
            return maidConfig.getSystemPrompt().trim();
        }
        return promptTemplateLoader.loadRequiredTemplate(maidProperties.getSystemPromptLocation());
    }

    private String resolveTaskPromptTemplate(AiMaidPromptScene scene, AiAdminConfigDTO.MaidConfig maidConfig) {
        if (maidConfig != null) {
            if (scene == AiMaidPromptScene.HELPER && StringUtils.hasText(maidConfig.getHelperPrompt())) {
                return maidConfig.getHelperPrompt().trim();
            }
            if (scene == AiMaidPromptScene.COMPANION && StringUtils.hasText(maidConfig.getCompanionPrompt())) {
                return maidConfig.getCompanionPrompt().trim();
            }
        }
        return promptTemplateLoader.loadRequiredTemplate(scene.resolveTaskPromptLocation(maidProperties));
    }

    private String renderPersonaTemplate(String template, AiAdminConfigDTO.MaidConfig maidConfig) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PLACEHOLDER_MAID_NAME, resolveMaidName(maidConfig));
        values.put(PLACEHOLDER_PERSONA_VERSION, resolvePersonaVersion(maidConfig));

        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        return rendered;
    }

    private String buildRuntimeContextBlock(AiMaidPromptScene scene, AiMaidPromptContext context, AiAdminConfigDTO.MaidConfig maidConfig) {
        StringBuilder builder = new StringBuilder();
        builder.append("### Runtime context").append(LINE_SEPARATOR);
        builder.append("- scene: ").append(scene.name().toLowerCase()).append(LINE_SEPARATOR);
        builder.append("- maidName: ").append(resolveMaidName(maidConfig)).append(LINE_SEPARATOR);
        builder.append("- personaVersion: ").append(resolvePersonaVersion(maidConfig)).append(LINE_SEPARATOR);
        builder.append("- pageContext: ").append(normalizeText(context.pageContext(), UNKNOWN_VALUE)).append(LINE_SEPARATOR);
        builder.append("- currentArticleId: ").append(context.currentArticleId() == null ? NONE_VALUE : context.currentArticleId()).append(LINE_SEPARATOR);
        builder.append("- currentArticleTitle: ").append(normalizeText(context.currentArticleTitle(), NONE_VALUE)).append(LINE_SEPARATOR);
        builder.append("- citationsRequired: ").append(context.citationsRequired()).append(LINE_SEPARATOR);
        builder.append("- allowCasualConversation: ").append(context.allowCasualConversation()).append(LINE_SEPARATOR);
        builder.append("- instruction: 回答时必须服从以上运行时边界；若上下文缺失，不要自行脑补站内事实。");
        return builder.toString();
    }

    private String resolveMaidName(AiAdminConfigDTO.MaidConfig maidConfig) {
        if (maidConfig != null && StringUtils.hasText(maidConfig.getName())) {
            return maidConfig.getName().trim();
        }
        return normalizeText(maidProperties.getName(), "Lyra");
    }

    private String resolvePersonaVersion(AiAdminConfigDTO.MaidConfig maidConfig) {
        if (maidConfig != null && StringUtils.hasText(maidConfig.getPersonaVersion())) {
            return maidConfig.getPersonaVersion().trim();
        }
        return normalizeText(maidProperties.getPersonaVersion(), "v1.1");
    }

    private String normalizeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
