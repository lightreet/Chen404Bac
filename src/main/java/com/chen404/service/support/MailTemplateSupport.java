package com.chen404.service.support;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.service.SiteConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MailTemplateSupport {

    private static final String DEFAULT_SITE_NAME = "Chen404 Blog";
    private static final String DEFAULT_SITE_DESCRIPTION = "一个写下技术，也收藏温柔日常的小小角落";

    private final SiteConfigService siteConfigService;
    private final String frontendBaseUrl;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public MailTemplateSupport(
            SiteConfigService siteConfigService,
            @Value("${app.frontend-base-url:http://localhost:20204}") String frontendBaseUrl
    ) {
        this.siteConfigService = siteConfigService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String render(String templatePath, Map<String, String> variables) {
        String content = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        String rendered = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    public Map<String, String> buildBrandVariables() {
        SiteConfigDTO config = siteConfigService.getConfig();
        String siteName = safeText(resolveSiteName(config));
        String siteDescription = safeText(resolveSiteDescription(config));

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("siteName", siteName);
        variables.put("siteDescription", siteDescription);
        variables.put("brandVisual", buildBrandVisual(config, siteName));
        return variables;
    }

    public String safeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public String safeAttribute(String value) {
        return safeText(value).replace("'", "&#39;");
    }

    private String loadTemplate(String templatePath) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("邮件模板加载失败: " + templatePath, e);
        }
    }

    private String resolveSiteName(SiteConfigDTO config) {
        if (config != null && StringUtils.hasText(config.getSiteName())) {
            return config.getSiteName().trim();
        }
        return DEFAULT_SITE_NAME;
    }

    private String resolveSiteDescription(SiteConfigDTO config) {
        if (config != null && StringUtils.hasText(config.getSiteDescription())) {
            return config.getSiteDescription().trim();
        }
        return DEFAULT_SITE_DESCRIPTION;
    }

    private String buildBrandVisual(SiteConfigDTO config, String safeSiteName) {
        String logoUrl = resolveAbsoluteLogoUrl(config);
        if (StringUtils.hasText(logoUrl)) {
            return """
                    <div style="width: 56px; height: 56px; border-radius: 18px; background: linear-gradient(145deg, rgba(255,255,255,0.96), rgba(255,247,251,0.98)); border: 1px solid rgba(233, 204, 220, 0.72); box-shadow: 0 12px 24px rgba(213, 143, 176, 0.12); display: inline-flex; align-items: center; justify-content: center;">
                      <img src="%s" alt="%s" style="width: 34px; height: 34px; object-fit: contain; display: block;" />
                    </div>
                    """.formatted(safeAttribute(logoUrl), safeSiteName);
        }

        return """
                <div style="width: 56px; height: 56px; border-radius: 18px; background: linear-gradient(145deg, rgba(255,255,255,0.96), rgba(255,243,248,0.98)); border: 1px solid rgba(233, 204, 220, 0.72); box-shadow: 0 12px 24px rgba(213, 143, 176, 0.12); display: inline-flex; align-items: center; justify-content: center; color: #d85f8f; font-size: 15px; font-weight: 800; letter-spacing: 0.08em;">
                  C404
                </div>
                """;
    }

    private String resolveAbsoluteLogoUrl(SiteConfigDTO config) {
        if (config == null || !StringUtils.hasText(config.getSiteLogo())) {
            return null;
        }
        String logo = config.getSiteLogo().trim();
        if (logo.startsWith("http://") || logo.startsWith("https://")) {
            return logo;
        }
        if (!StringUtils.hasText(frontendBaseUrl)) {
            return null;
        }
        if (logo.startsWith("/")) {
            return joinUrl(frontendBaseUrl, logo);
        }
        return joinUrl(frontendBaseUrl, "/" + logo);
    }

    private String joinUrl(String baseUrl, String path) {
        String safeBaseUrl = baseUrl.trim();
        if (safeBaseUrl.endsWith("/") && path.startsWith("/")) {
            return safeBaseUrl.substring(0, safeBaseUrl.length() - 1) + path;
        }
        if (!safeBaseUrl.endsWith("/") && !path.startsWith("/")) {
            return safeBaseUrl + "/" + path;
        }
        return safeBaseUrl + path;
    }
}
