package com.chen404.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.springframework.stereotype.Service;

/**
 * 管理用户级会话版本，使密码变更可以一次性失效该用户的所有访问与刷新令牌。
 */
@Service
public class AuthSessionService {

    private static final long INITIAL_SESSION_VERSION = 0L;
    private static final String SESSION_VERSION_CLAIM = "sessionVersion";

    private final RedisUtil redisUtil;

    public AuthSessionService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 获取签发新令牌时应写入的当前会话版本。
     */
    public long getCurrentVersion(Long userId) {
        if (userId == null) {
            return INITIAL_SESSION_VERSION;
        }
        String rawVersion = redisUtil.getString(RedisKeys.authSessionVersion(userId));
        if (rawVersion == null) {
            return INITIAL_SESSION_VERSION;
        }
        try {
            return Long.parseLong(rawVersion);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("用户会话版本数据异常", ex);
        }
    }

    /**
     * 检查已验证签名的令牌是否属于用户当前会话版本。
     */
    public boolean isCurrent(Long userId, DecodedJWT decodedJWT) {
        if (userId == null || decodedJWT == null) {
            return false;
        }
        Long tokenVersion = decodedJWT.getClaim(SESSION_VERSION_CLAIM).asLong();
        return tokenVersion != null && tokenVersion == getCurrentVersion(userId);
    }

    /**
     * 提升用户会话版本，立即失效此前签发的全部访问与刷新令牌。
     */
    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }
        Long version = redisUtil.increment(RedisKeys.authSessionVersion(userId));
        if (version == null) {
            throw new IllegalStateException("用户会话撤销失败");
        }
    }
}
