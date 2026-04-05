package com.chen404.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * API请求日志拦截器
 * 记录请求入参、出参、执行时间等信息
 */
@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_KEY = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());
        logRequest(request);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        logResponse(request, response, ex);
    }

    /** 慢请求阈值（毫秒），超过则打 WARN */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    /**
     * 记录请求信息：默认单行摘要，符合阿里巴巴日志规约
     */
    private void logRequest(HttpServletRequest request) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
            String clientIp = getClientIp(request);
            String traceId = MDC.get("traceId");
            log.info("[API-REQ] traceId={} method={} uri={} clientIp={}", traceId, method, uri + query, clientIp);
            if (log.isDebugEnabled()) {
                logDebugRequest(request);
            }
        } catch (Exception e) {
            log.warn("记录请求日志失败: {}", e.getMessage());
        }
    }

    /** DEBUG 下输出请求详情（参数、Body 脱敏） */
    private void logDebugRequest(HttpServletRequest request) {
        try {
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);
                if (isImportantHeader(name)) {
                    headers.put(name, maskHeaderValue(name, value));
                }
            }
            if (!headers.isEmpty()) {
                log.debug("[API-REQ] headers={}", formatMap(headers));
            }
            if (!isMultipartRequest(request)) {
                Map<String, String[]> paramMap = request.getParameterMap();
                if (!paramMap.isEmpty()) {
                    Map<String, String> params = new HashMap<>();
                    paramMap.forEach((k, v) -> params.put(k, maskSensitiveField(k, v.length == 1 ? v[0] : String.join(",", v))));
                    log.debug("[API-REQ] params={}", formatMap(params));
                }
            }
            if (isJsonRequest(request)) {
                String body = getRequestBody(request);
                if (body != null && !body.isEmpty()) {
                    log.debug("[API-REQ] body={}", maskSensitiveJson(body));
                }
            }
        } catch (Exception e) {
            log.debug("记录请求详情失败: {}", e.getMessage());
        }
    }

    /**
     * 记录响应信息：单行摘要，异常或慢请求时提升级别
     */
    private void logResponse(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_KEY);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            String traceId = MDC.get("traceId");
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            if (ex != null) {
                log.error("[API-RES] traceId={} method={} uri={} status={} duration={}ms error={}", traceId, method, uri, status, duration, ex.getMessage(), ex);
            } else if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("[API-RES] traceId={} method={} uri={} status={} duration={}ms slow", traceId, method, uri, status, duration);
            } else {
                log.info("[API-RES] traceId={} method={} uri={} status={} duration={}ms", traceId, method, uri, status, duration);
            }
        } catch (Exception e) {
            log.warn("记录响应日志失败: {}", e.getMessage());
        }
    }

    /**
     * 格式化Map为字符串
     */
    private String formatMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    /**
     * 判断是否是关键header
     */
    private boolean isImportantHeader(String name) {
        String lower = name.toLowerCase();
        return lower.equals("content-type") ||
               lower.equals("authorization") ||
               lower.equals("user-agent") ||
               lower.equals("x-trace-id");
    }

    /**
     * 脱敏header值
     */
    private String maskHeaderValue(String name, String value) {
        if (value == null) {
            return null;
        }
        String lower = name.toLowerCase();
        if (lower.equals("authorization") && value.startsWith("Bearer ") && value.length() > 20) {
            return "Bearer " + value.substring(7, 15) + "...";
        }
        return value;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 判断是否为JSON请求
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 获取请求Body
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof com.chen404.filter.RequestBodyCacheFilter.CachedBodyHttpServletRequest) {
                com.chen404.filter.RequestBodyCacheFilter.CachedBodyHttpServletRequest cachedRequest =
                    (com.chen404.filter.RequestBodyCacheFilter.CachedBodyHttpServletRequest) request;
                byte[] cachedBody = cachedRequest.getCachedBody();
                return new String(cachedBody, request.getCharacterEncoding());
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 脱敏敏感字段
     */
    private String maskSensitiveField(String key, String value) {
        if (value == null) {
            return null;
        }
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("password") || lowerKey.contains("pwd") || lowerKey.contains("secret")) {
            return "******";
        }
        if (lowerKey.contains("token") || lowerKey.contains("authorization")) {
            return value.length() > 10 ? value.substring(0, 10) + "..." : "***";
        }
        if (lowerKey.contains("phone") && value.length() == 11) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        if (lowerKey.contains("email") && value.contains("@")) {
            int atIndex = value.indexOf('@');
            if (atIndex > 2) {
                return value.substring(0, 2) + "***" + value.substring(atIndex);
            }
        }
        return value;
    }

    /**
     * 脱敏JSON中的敏感字段
     */
    private String maskSensitiveJson(String json) {
        try {
            return json.replaceAll("(\"password\\s*\"\\s*:\\s*\")([^\"]*)(\")", "$1******$3")
                       .replaceAll("(\"token\\s*\"\\s*:\\s*\")([^\"]{10})[^\"]*(\")", "$1$2...$3")
                       .replaceAll("(\"phone\\s*\"\\s*:\\s*\")(\\d{3})\\d{4}(\\d{4})(\")", "$1$2****$4");
        } catch (Exception e) {
            return json;
        }
    }
}
