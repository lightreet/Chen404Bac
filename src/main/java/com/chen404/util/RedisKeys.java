package com.chen404.util;

/**
 * Redis Key 命名规范（统一前缀，避免冲突，便于运维排查）
 *
 * 约定：chen404:{module}:{biz}:{...}
 */
public final class RedisKeys {
    private RedisKeys() {}

    public static final String PREFIX = "chen404:";

    public static String verifyCode(String type, String target) {
        return PREFIX + "verify:code:" + type + ":" + target;
    }

    public static String verifyInterval(String type, String target) {
        return PREFIX + "verify:interval:" + type + ":" + target;
    }

    public static String verifyDailyCount(String type, String target) {
        return PREFIX + "verify:daily:" + type + ":" + target;
    }
}

