package com.chen404.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

@Component
public class PublicApiRequestMatcher implements RequestMatcher {

    private static final String API_PREFIX = "/api";

    @Override
    public boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        String path = normalizePath(request.getRequestURI());

        if (isSwaggerPath(path) || isStaticPath(path)) {
            return true;
        }

        if (path.equals("/auth/login")
                || path.equals("/auth/register")
                || path.equals("/auth/send-code")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/refresh")) {
            return "POST".equalsIgnoreCase(method);
        }

        if (path.equals("/auth/check-username")
                || path.equals("/auth/check-email")
                || path.equals("/auth/check-phone")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (path.equals("/categories") || path.startsWith("/categories/")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (path.equals("/emoji") || path.startsWith("/emoji/")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (path.equals("/articles") || path.startsWith("/articles/")) {
            return isPublicArticleRequest(path, method);
        }

        if (path.equals("/comments") || path.startsWith("/comments/")) {
            return isPublicCommentRequest(path, method);
        }

        if (path.equals("/music") || path.startsWith("/music/")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (path.equals("/travel-memories") || path.startsWith("/travel-memories/")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (path.equals("/ai/chat") || path.equals("/ai/chat/stream")) {
            return "POST".equalsIgnoreCase(method);
        }

        if (path.startsWith("/ai/chat/sessions/")) {
            return "GET".equalsIgnoreCase(method);
        }

        return path.equals("/home")
                || path.startsWith("/home/")
                || path.equals("/site")
                || path.startsWith("/site/")
                || path.equals("/trust-requests/email-approve")
                || path.equals("/tags")
                || path.startsWith("/tags/")
                || path.equals("/archives")
                || path.startsWith("/archives/");
    }

    private boolean isPublicArticleRequest(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            if (path.equals("/articles")
                    || path.equals("/articles/hot")
                    || path.equals("/articles/recommend")) {
                return true;
            }
            if (path.equals("/articles/mine")
                    || path.equals("/articles/mine/liked")
                    || path.equals("/articles/mine/favorites")) {
                return false;
            }
            return path.matches("/articles/[^/]+") || path.matches("/articles/[^/]+/neighbors");
        }
        return "POST".equalsIgnoreCase(method) && path.matches("/articles/[^/]+/like");
    }

    private boolean isPublicCommentRequest(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/comments") || path.matches("/comments/[^/]+/like");
        }
        return "DELETE".equalsIgnoreCase(method) && path.matches("/comments/[^/]+");
    }

    private boolean isSwaggerPath(String path) {
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")
                || path.equals("/swagger-ui.html");
    }

    private boolean isStaticPath(String path) {
        return path.equals("/")
                || path.startsWith("/uploads/")
                || path.startsWith("/static/")
                || path.equals("/favicon.ico");
    }

    private String normalizePath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        if (requestUri.startsWith(API_PREFIX)) {
            return requestUri.substring(API_PREFIX.length());
        }
        return requestUri;
    }
}
