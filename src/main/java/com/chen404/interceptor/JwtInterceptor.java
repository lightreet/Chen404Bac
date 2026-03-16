package com.chen404.interceptor;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.chen404.domain.Result;
import com.chen404.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT认证拦截器
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头中获取token
        String authHeader = request.getHeader("Authorization");

        // 允许匿名访问的请求直接放行
        String uri = request.getRequestURI();
        if (isPublicUri(uri)) {
            return true;
        }

        // 检查Authorization头
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(new ObjectMapper().writeValueAsString(
                Result.error(401, "未授权，请先登录")
            ));
            return false;
        }

        // 提取token
        String token = authHeader.substring(7);

        try {
            // 验证token
            jwtUtil.verifyToken(token);

            // 将用户ID存入request属性，供后续使用
            Long userId = jwtUtil.getUserIdFromToken(token);
            request.setAttribute("userId", userId);

            return true;
        } catch (JWTVerificationException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(new ObjectMapper().writeValueAsString(
                Result.error(401, "Token无效或已过期")
            ));
            return false;
        }
    }

    /**
     * 判断是否为公开URI
     */
    private boolean isPublicUri(String uri) {
        return uri.startsWith("/api/auth/") ||
               uri.startsWith("/api/upload/") ||
               uri.startsWith("/api/home/") ||
               uri.startsWith("/api/site/") ||
               uri.startsWith("/api/articles/") ||
               uri.startsWith("/api/categories/") ||
               uri.startsWith("/api/tags/") ||
               uri.startsWith("/api/archives/") ||
               uri.startsWith("/api/comments/") ||
               uri.startsWith("/api/friends/");
    }
}
