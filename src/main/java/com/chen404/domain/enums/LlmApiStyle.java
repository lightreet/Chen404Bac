package com.chen404.domain.enums;

import java.util.Locale;

/** 已接入的模型协议；配置、场景请求及 HTTP 客户端共享解析和兼容默认值。 */
public enum LlmApiStyle {
    CHAT_COMPLETIONS("chat-completions"),
    RESPONSES("responses");

    private final String value;

    LlmApiStyle(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 保留旧配置兼容性：空值或未识别值使用 chat-completions。 */
    public static LlmApiStyle fromValue(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (LlmApiStyle style : values()) {
            if (style.value.equals(normalized)) {
                return style;
            }
        }
        return CHAT_COMPLETIONS;
    }
}
