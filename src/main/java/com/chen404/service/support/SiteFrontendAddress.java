package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import java.net.URI;
import java.util.Locale;

/** 网站对外入口地址的统一校验；禁止将账号信息、路径或查询参数混入扫码地址。 */
public final class SiteFrontendAddress {
    private SiteFrontendAddress() { }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                    || uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException();
            return value.trim().replaceFirst("/+$", "");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("网站访问地址应为完整的 http 或 https 域名地址，不包含路径、参数或账号信息");
        }
    }

    public static String forMobileUpload(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) throw unavailable();
        String host = URI.create(normalized).getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.")
                || host.equals("0.0.0.0") || host.equals("[::1]")) throw unavailable();
        return normalized;
    }

    private static BadRequestException unavailable() {
        return new BadRequestException("请管理员在站点基础信息中设置网站访问地址后重试");
    }
}
