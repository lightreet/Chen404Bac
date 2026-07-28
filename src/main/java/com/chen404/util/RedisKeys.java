package com.chen404.util;

import com.chen404.domain.enums.VerificationCodeTypeEnum;

/**
 * Redis Key 命名规范（统一前缀，避免冲突，便于运维排查）
 *
 * 约定：chen404:{module}:{biz}:{...}
 */
public final class RedisKeys {
    private RedisKeys() {}

    public static final String PREFIX = "chen404:";

    public static String verifyCode(VerificationCodeTypeEnum type, String target) {
        return PREFIX + "verify:code:" + type.getCode() + ":" + target;
    }

    public static String verifyInterval(VerificationCodeTypeEnum type, String target) {
        return PREFIX + "verify:interval:" + type.getCode() + ":" + target;
    }

    public static String verifyDailyCount(VerificationCodeTypeEnum type, String target) {
        return PREFIX + "verify:daily:" + type.getCode() + ":" + target;
    }

    /** 匿名文章点赞冷却（与 IP 绑定） */
    public static String articleLikeThrottle(Long articleId, String clientIp) {
        return PREFIX + "article:like:throttle:" + articleId + ":" + sanitizeIp(clientIp);
    }

    /** 匿名评论点赞冷却 */
    public static String commentLikeThrottle(Long commentId, String clientIp) {
        return PREFIX + "comment:like:throttle:" + commentId + ":" + sanitizeIp(clientIp);
    }

    public static String commentCreateThrottle(String scope) {
        return PREFIX + "comment:create:throttle:" + scope;
    }

    public static String loginFailCount(String scope) {
        return PREFIX + "auth:login:fail:count:" + scope;
    }

    public static String loginBlock(String scope) {
        return PREFIX + "auth:login:block:" + scope;
    }

    public static String refreshTokenBlacklist(String tokenId) {
        return PREFIX + "auth:refresh:blacklist:" + tokenId;
    }

    /** 登录用户音乐播放现场 */
    public static String musicPlayerState(Long userId) {
        return PREFIX + "music:player:state:user:" + userId;
    }

    private static String sanitizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "anonymous";
        }
        return ip.trim().replace(':', '_');
    }
}
