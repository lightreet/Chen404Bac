package com.chen404.service.impl;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.service.SiteConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SiteConfigServiceImpl implements SiteConfigService {

    private final ObjectMapper objectMapper;
    private final Path configPath;

    public SiteConfigServiceImpl(
            ObjectMapper objectMapper,
            @Value("${app.site-config.path:}") String configuredPath) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.configPath = (StringUtils.hasText(configuredPath)
                ? Path.of(configuredPath.trim())
                : Path.of(System.getProperty("user.dir"), "data", "site-config.json"))
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public synchronized SiteConfigDTO getConfig() {
        SiteConfigDTO config = loadFromFile();
        return mergeWithDefaults(config);
    }

    @Override
    public synchronized SiteConfigDTO updateConfig(SiteConfigDTO patch) {
        SiteConfigDTO current = getConfig();
        applyPatch(current, patch);
        normalize(current);
        writeToFile(current);
        return current;
    }

    private SiteConfigDTO loadFromFile() {
        if (!Files.exists(configPath)) {
            return defaults();
        }
        try {
            SiteConfigDTO config = objectMapper.readValue(configPath.toFile(), SiteConfigDTO.class);
            return config == null ? defaults() : config;
        } catch (IOException e) {
            log.error("读取站点配置失败，回退默认配置: {}", configPath, e);
            return defaults();
        }
    }

    private void writeToFile(SiteConfigDTO config) {
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(configPath.toFile(), config);
        } catch (IOException e) {
            log.error("写入站点配置失败: {}", configPath, e);
            throw new IllegalStateException("保存站点配置失败");
        }
    }

    private static SiteConfigDTO mergeWithDefaults(SiteConfigDTO config) {
        SiteConfigDTO merged = defaults();
        applyPatch(merged, config);
        normalize(merged);
        return merged;
    }

    private static SiteConfigDTO defaults() {
        SiteConfigDTO config = new SiteConfigDTO();
        config.setSiteName("Chen404 Blog");
        config.setSiteDescription("一个热爱技术分享的博客");
        config.setSiteLogo("/logo.svg");
        config.setSiteFavicon("/favicon.ico");
        config.setIcp("");
        config.setGithub("https://github.com/chen404");
        config.setEmail("admin@chen404.com");
        config.setHeroImages(new LinkedHashMap<>());
        return config;
    }

    private static void applyPatch(SiteConfigDTO target, SiteConfigDTO patch) {
        if (patch == null) return;
        if (patch.getSiteName() != null) target.setSiteName(patch.getSiteName());
        if (patch.getSiteDescription() != null) target.setSiteDescription(patch.getSiteDescription());
        if (patch.getSiteLogo() != null) target.setSiteLogo(patch.getSiteLogo());
        if (patch.getSiteFavicon() != null) target.setSiteFavicon(patch.getSiteFavicon());
        if (patch.getIcp() != null) target.setIcp(patch.getIcp());
        if (patch.getGithub() != null) target.setGithub(patch.getGithub());
        if (patch.getEmail() != null) target.setEmail(patch.getEmail());
        if (patch.getHeroImages() != null) {
            Map<String, String> merged = new LinkedHashMap<>();
            if (target.getHeroImages() != null) {
                merged.putAll(target.getHeroImages());
            }
            for (Map.Entry<String, String> entry : patch.getHeroImages().entrySet()) {
                String key = entry.getKey();
                if (!StringUtils.hasText(key)) continue;
                String normalizedKey = key.trim();
                String value = entry.getValue();
                if (!StringUtils.hasText(value)) {
                    merged.remove(normalizedKey);
                    continue;
                }
                merged.put(normalizedKey, value.trim());
            }
            target.setHeroImages(merged);
        }
    }

    private static void normalize(SiteConfigDTO config) {
        config.setSiteName(trimToDefault(config.getSiteName(), "Chen404 Blog"));
        config.setSiteDescription(trimToDefault(config.getSiteDescription(), "一个热爱技术分享的博客"));
        config.setSiteLogo(trimToDefault(config.getSiteLogo(), "/logo.svg"));
        config.setSiteFavicon(trimToDefault(config.getSiteFavicon(), "/favicon.ico"));
        config.setIcp(trimToDefault(config.getIcp(), ""));
        config.setGithub(trimToDefault(config.getGithub(), "https://github.com/chen404"));
        config.setEmail(trimToDefault(config.getEmail(), "admin@chen404.com"));

        Map<String, String> heroImages = new LinkedHashMap<>();
        if (config.getHeroImages() != null) {
            for (Map.Entry<String, String> entry : config.getHeroImages().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!StringUtils.hasText(key)) continue;
                if (!StringUtils.hasText(value)) continue;
                String normalizedValue = value.trim();
                if (!isSafeHeroImageUrl(normalizedValue)) continue;
                heroImages.put(key.trim(), normalizedValue);
            }
        }
        config.setHeroImages(heroImages);
    }

    private static boolean isSafeHeroImageUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        String url = value.trim();
        if (url.startsWith("/")) {
            return !containsUnsafeCssChars(url);
        }
        if ((url.startsWith("http://") || url.startsWith("https://")) && !containsUnsafeCssChars(url)) {
            return true;
        }
        return false;
    }

    private static boolean containsUnsafeCssChars(String value) {
        return value.contains("\"")
                || value.contains("'")
                || value.contains("(")
                || value.contains(")")
                || value.contains("<")
                || value.contains(">")
                || value.contains("\\")
                || value.contains("\r")
                || value.contains("\n");
    }

    private static String trimToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
