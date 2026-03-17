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
        // 允许匿名访问的请求直接放行
        String uri = request.getRequestURI();
        if (isPublicUri(uri)) {
            return true;
        }

        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头中获取token
        String authHeader = request.getHeader("Authorization");

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
        // 去除 context-path 后的路径（如果存在）
        String path = uri;
        if (uri.startsWith("/api")) {
            path = uri.substring(4); // 去掉 /api 前缀
        }

        // Swagger/OpenAPI 相关路径
        if (path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-resources") ||
            path.startsWith("/webjars") ||
            path.equals("/swagger-ui.html")) {
            return true;
        }

        // 仅以下 /auth 子路径为公开（登录、注册、验证码、校验接口），其余 /auth/* 需 JWT（如 /auth/info、/auth/profile、/auth/change-password）
        if (path.equals("/auth") || path.startsWith("/auth/")) {
            return path.equals("/auth/login") || path.equals("/auth/register")
                    || path.equals("/auth/send-code") || path.equals("/auth/refresh")
                    || path.equals("/auth/check-username") || path.equals("/auth/check-email") || path.equals("/auth/check-phone");
        }
        // 其他公开API路径
        return path.equals("/home") || path.startsWith("/home/") ||
               path.equals("/site") || path.startsWith("/site/") ||
               path.equals("/articles") || path.startsWith("/articles/") ||
               path.equals("/categories") || path.startsWith("/categories/") ||
               path.equals("/tags") || path.startsWith("/tags/") ||
               path.equals("/archives") || path.startsWith("/archives/") ||
               path.equals("/comments") || path.startsWith("/comments/") ||
               path.equals("/friends") || path.startsWith("/friends/");
    }
}
