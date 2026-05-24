package com.chen404.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求辅助工具，统一处理客户端 IP 等跨控制器逻辑。
 */
public final class WebRequestUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String[] CLIENT_IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private WebRequestUtil() {
    }

    /**
     * 按代理头优先级解析客户端 IP，并兼容本机 IPv6 回环地址。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : CLIENT_IP_HEADERS) {
            String ip = firstValidIp(request.getHeader(header));
            if (ip != null) {
                return normalizeLoopback(ip);
            }
        }
        String remoteAddr = firstValidIp(request.getRemoteAddr());
        return remoteAddr == null ? UNKNOWN : normalizeLoopback(remoteAddr);
    }

    private static String firstValidIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank() || UNKNOWN.equalsIgnoreCase(rawIp)) {
            return null;
        }
        int commaIndex = rawIp.indexOf(',');
        String ip = commaIndex >= 0 ? rawIp.substring(0, commaIndex) : rawIp;
        ip = ip.trim();
        if (ip.isBlank() || UNKNOWN.equalsIgnoreCase(ip)) {
            return null;
        }
        return ip;
    }

    private static String normalizeLoopback(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return LOCALHOST_IPV4;
        }
        return ip;
    }
}
