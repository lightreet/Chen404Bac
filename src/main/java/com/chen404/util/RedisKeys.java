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

    /** 匿名文章点赞冷却（与 IP 绑定） */
    public static String articleLikeThrottle(Long articleId, String clientIp) {
        return PREFIX + "article:like:throttle:" + articleId + ":" + sanitizeIp(clientIp);
    }

    /** 匿名评论点赞冷却 */
    public static String commentLikeThrottle(Long commentId, String clientIp) {
        return PREFIX + "comment:like:throttle:" + commentId + ":" + sanitizeIp(clientIp);
    }

    private static String sanitizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "anonymous";
        }
        return ip.trim().replace(':', '_');
    }
}

