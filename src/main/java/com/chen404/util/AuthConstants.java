package com.chen404.util;

    /**
     * 认证相关常量
     */
public final class AuthConstants {

    private AuthConstants() {}

    public static final int SEND_CODE_EXPIRE_SECONDS = 300;
    public static final int SEND_CODE_INTERVAL_SECONDS = 60;
    public static final int MAX_DAILY_SEND_CODE_COUNT = 10;
    public static final int VERIFY_CODE_LENGTH = 6;
    public static final String EMAIL_TARGET_SYMBOL = "@";
    public static final String REDIS_FLAG_VALUE = "1";
}
