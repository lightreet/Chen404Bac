package com.chen404.util;

import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从当前线程的 Spring Security principal 中读取登录用户。
 */
public final class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    public static Long requireUserId(AuthenticatedUser currentUser) {
        Long userId = getUserId(currentUser);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    public static Long getUserId(AuthenticatedUser currentUser) {
        return currentUser == null ? null : currentUser.getUserId();
    }

    public static AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        if (principal instanceof Long userId) {
            return new AuthenticatedUser(userId, null, null);
        }
        return null;
    }

    public static Long getCurrentUserId() {
        return getUserId(getCurrentUser());
    }
}
