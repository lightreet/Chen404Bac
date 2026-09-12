package com.chen404.domain;

/** 账号注册、资料更新和密码重置共用的字段边界，与 sys_user 列容量保持一致。 */
public final class UserConstraints {
    public static final int EMAIL_MAX_LENGTH = 100;
    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 100;
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 20;
    public static final int VERIFICATION_CODE_MIN_LENGTH = 4;
    public static final int VERIFICATION_CODE_MAX_LENGTH = 6;

    private UserConstraints() {
    }
}
