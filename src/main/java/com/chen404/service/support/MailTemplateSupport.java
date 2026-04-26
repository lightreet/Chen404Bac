package com.chen404.service.support;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.service.SiteConfigService;
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
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public MailTemplateSupport(SiteConfigService siteConfigService) {
        this.siteConfigService = siteConfigService;
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
                    <div style="width: 64px; height: 64px; border-radius: 22px; background: rgba(255,255,255,0.88); box-shadow: 0 14px 30px rgba(245,155,188,0.18); display: inline-flex; align-items: center; justify-content: center;">
                      <img src="%s" alt="%s" style="width: 42px; height: 42px; object-fit: contain; display: block;" />
                    </div>
                    """.formatted(safeAttribute(logoUrl), safeSiteName);
        }

        return """
                <div style="width: 64px; height: 64px; border-radius: 22px; background: linear-gradient(135deg, rgba(255,255,255,0.94), rgba(255,243,248,0.96)); box-shadow: 0 14px 30px rgba(245,155,188,0.18); display: inline-flex; align-items: center; justify-content: center; color: #d85f8f; font-size: 18px; font-weight: 800; letter-spacing: 0.08em;">
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
        return null;
    }
}
