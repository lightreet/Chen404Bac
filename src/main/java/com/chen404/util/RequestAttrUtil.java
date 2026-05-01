package com.chen404.util;

import com.chen404.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * ??????????????? request attribute????? SecurityContext?
 */
public final class RequestAttrUtil {

    private RequestAttrUtil() {
    }

    public static Long requireUserId(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    public static Long getUserId(HttpServletRequest request) {
        if (request != null) {
            Object userId = request.getAttribute("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            }
        }
        return CurrentUserUtil.getCurrentUserId();
    }
}
