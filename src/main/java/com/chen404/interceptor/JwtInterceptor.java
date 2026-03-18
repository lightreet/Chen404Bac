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
        if (isPublicUri(request)) {
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
     * 判断是否为公开 URI（部分路径仅 GET 公开，如 /categories）
     */
    private boolean isPublicUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String path = uri.startsWith("/api") ? uri.substring(4) : uri;

        // Swagger/OpenAPI 相关路径
        if (path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-resources") ||
            path.startsWith("/webjars") ||
            path.equals("/swagger-ui.html")) {
            return true;
        }

        // 仅以下 /auth 子路径为公开
        if (path.equals("/auth") || path.startsWith("/auth/")) {
            return path.equals("/auth/login") || path.equals("/auth/register")
                    || path.equals("/auth/send-code") || path.equals("/auth/refresh")
                    || path.equals("/auth/check-username") || path.equals("/auth/check-email") || path.equals("/auth/check-phone");
        }

        // /categories 仅 GET 公开，POST/PUT/DELETE 需 JWT + 管理员
        if (path.equals("/categories") || path.startsWith("/categories/")) {
            return "GET".equalsIgnoreCase(method);
        }

        // /articles：列表、详情、热门、推荐、上一篇下一篇、点赞公开；/articles/mine 与 创建/更新/删除 需 JWT
        if (path.equals("/articles") || path.startsWith("/articles/")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/articles") || path.equals("/articles/hot") || path.equals("/articles/recommend")) {
                    return true;
                }
                if (path.equals("/articles/mine")) {
                    return false;
                }
                // /articles/{id} 或 /articles/{id}/neighbors
                if (path.matches("/articles/[^/]+") || path.matches("/articles/[^/]+/neighbors")) {
                    return true;
                }
            }
            if ("POST".equalsIgnoreCase(method) && path.matches("/articles/[^/]+/like")) {
                return true;
            }
            return false;
        }

        // 其他公开 API 路径
        return path.equals("/home") || path.startsWith("/home/") ||
               path.equals("/site") || path.startsWith("/site/") ||
               path.equals("/tags") || path.startsWith("/tags/") ||
               path.equals("/archives") || path.startsWith("/archives/") ||
               path.equals("/comments") || path.startsWith("/comments/") ||
               path.equals("/friends") || path.startsWith("/friends/");
    }
}
