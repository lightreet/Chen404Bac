package com.chen404.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求辅助工具，统一处理客户端 IP 等跨控制器逻辑。
 */
public final class WebRequestUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";

    private WebRequestUtil() {
    }

    /**
     * 读取容器已经解析后的远端地址，并兼容本机 IPv6 回环地址。
     * 原始转发头只能由受信代理交给容器处理，业务代码不直接信任客户端提交的 Header。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
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
