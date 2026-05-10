package com.chen404.service.support.prompt;

import com.chen404.config.AiMaidProperties;

/**
 * AI 女仆 prompt 场景枚举。
 * <p>
 * 第一阶段先区分站内问答与轻日常陪聊两类任务，
 * 后续如需扩展摘要、推荐、评论引导等场景，可继续在此处追加。
 */
public enum AiMaidPromptScene {

    HELPER,
    COMPANION;

    /**
     * 根据场景解析对应的任务 prompt 模板位置。
     *
     * @param maidProperties AI 女仆配置
     * @return 任务模板资源位置
     */
    public String resolveTaskPromptLocation(AiMaidProperties maidProperties) {
        return switch (this) {
            case HELPER -> maidProperties.getHelperTaskPromptLocation();
            case COMPANION -> maidProperties.getCompanionTaskPromptLocation();
        };
    }
}
