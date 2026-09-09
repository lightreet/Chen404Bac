package com.chen404.service.support;

import java.util.regex.Pattern;

/** 模型 JSON 输出的共同预处理；保留 JSON 内容，由各场景执行结构与业务校验。 */
public final class AiJsonOutput {
    private static final Pattern OPENING_FENCE = Pattern.compile("^```(?:json)?\\s*");
    private static final Pattern CLOSING_FENCE = Pattern.compile("\\s*```$");

    private AiJsonOutput() {
    }

    /** 兼容纯 JSON、Markdown JSON 代码块及空输出。 */
    public static String stripCodeFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutOpening = OPENING_FENCE.matcher(trimmed).replaceFirst("");
        return CLOSING_FENCE.matcher(withoutOpening).replaceFirst("");
    }
}
