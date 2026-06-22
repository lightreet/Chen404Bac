package com.chen404.domain.enums;

import lombok.Getter;

/**
 * 验证码业务类型枚举
 */
@Getter
public enum VerificationCodeTypeEnum {

    REGISTER("register", "注册", "Chen404博客 - 注册验证码"),
    LOGIN("login", "登录", "Chen404博客 - 登录验证码"),
    RESET("reset", "密码重置", "Chen404博客 - 密码重置验证码");

    private final String code;
    private final String displayName;
    private final String mailSubject;

    VerificationCodeTypeEnum(String code, String displayName, String mailSubject) {
        this.code = code;
        this.displayName = displayName;
        this.mailSubject = mailSubject;
    }

    public boolean matches(String rawCode) {
        return rawCode != null && code.equalsIgnoreCase(rawCode.trim());
    }

    public static VerificationCodeTypeEnum fromCode(String rawCode) {
        if (rawCode != null) {
            for (VerificationCodeTypeEnum value : values()) {
                if (value.matches(rawCode)) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException("验证码类型不合法");
    }
}
