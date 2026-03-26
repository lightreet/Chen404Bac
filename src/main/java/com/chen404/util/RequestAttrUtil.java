package com.chen404.util;

import com.chen404.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求属性中获取 JWT 解析后的用户 ID，统一“需登录”逻辑，避免各 Controller 重复判断。
 */
public final class RequestAttrUtil {

    private RequestAttrUtil() {}

    /**
     * 获取当前登录用户 ID，未登录时抛出 UnauthorizedException（全局处理器返回 401）
     */
    public static Long requireUserId(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    /**
     * 获取当前请求中的用户 ID；未登录时返回 null。
     */
    public static Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
