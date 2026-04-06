package com.chen404.service.impl;

import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.domain.entity.SysFile;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.SiteConfigService;
import com.chen404.service.SysFileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SiteConfigServiceImpl implements SiteConfigService {

    private static final long SITE_CONFIG_REF_ID = 1L;

    private static final String KEY_SITE_NAME = "site.name";
    private static final String KEY_SITE_DESCRIPTION = "site.description";
    private static final String KEY_SITE_LOGO = "site.logo";
    private static final String KEY_SITE_FAVICON = "site.favicon";
    private static final String KEY_SITE_ICP = "site.icp";
    private static final String KEY_SITE_GITHUB = "site.github";
    private static final String KEY_SITE_EMAIL = "site.email";
    private static final String KEY_HERO_IMAGES = "site.hero_images";

    private final ObjectMapper objectMapper;
    private final SiteConfigMapper siteConfigMapper;
    private final SysFileService sysFileService;

    public SiteConfigServiceImpl(
            ObjectMapper objectMapper,
            SiteConfigMapper siteConfigMapper,
            SysFileService sysFileService) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.siteConfigMapper = siteConfigMapper;
        this.sysFileService = sysFileService;
    }

    @Override
    public synchronized SiteConfigDTO getConfig() {
        return mergeWithDefaults(loadFromDatabase());
    }

    @Override
    @Transactional
    public synchronized SiteConfigDTO updateConfig(SiteConfigDTO patch) {
        SiteConfigDTO current = getConfig();
        applyPatch(current, patch);
        normalize(current);
        writeToDatabase(current);
        persistHeroImages(current);
        return current;
    }

    private SiteConfigDTO loadFromDatabase() {
        List<SiteConfig> rows = siteConfigMapper.selectAllConfigs();
        if (rows == null || rows.isEmpty()) {
            SiteConfigDTO config = defaults();
            writeToDatabase(config);
            persistHeroImages(config);
            return config;
        }

        SiteConfigDTO dto = new SiteConfigDTO();
        for (SiteConfig row : rows) {
            if (row == null || !StringUtils.hasText(row.getConfigKey())) {
                continue;
            }
            String key = row.getConfigKey().trim();
            String value = row.getConfigValue();
            switch (key) {
                case KEY_SITE_NAME -> dto.setSiteName(value);
                case KEY_SITE_DESCRIPTION -> dto.setSiteDescription(value);
                case KEY_SITE_LOGO -> dto.setSiteLogo(value);
                case KEY_SITE_FAVICON -> dto.setSiteFavicon(value);
                case KEY_SITE_ICP -> dto.setIcp(value);
                case KEY_SITE_GITHUB -> dto.setGithub(value);
                case KEY_SITE_EMAIL -> dto.setEmail(value);
                case KEY_HERO_IMAGES -> dto.setHeroImages(parseHeroImages(value));
                default -> {
                }
            }
        }
        return dto;
    }

    private void writeToDatabase(SiteConfigDTO config) {
        Map<String, SiteConfig> existing = siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        upsertValue(existing, KEY_SITE_NAME, config.getSiteName(), "Site name", 1);
        upsertValue(existing, KEY_SITE_DESCRIPTION, config.getSiteDescription(), "Site description", 1);
        upsertValue(existing, KEY_SITE_LOGO, config.getSiteLogo(), "Site logo", 1);
        upsertValue(existing, KEY_SITE_FAVICON, config.getSiteFavicon(), "Site favicon", 1);
        upsertValue(existing, KEY_SITE_ICP, config.getIcp(), "ICP number", 1);
        upsertValue(existing, KEY_SITE_GITHUB, config.getGithub(), "GitHub link", 1);
        upsertValue(existing, KEY_SITE_EMAIL, config.getEmail(), "Contact email", 1);
        upsertValue(existing, KEY_HERO_IMAGES, toHeroImagesJson(config.getHeroImages()), "Hero images", 4);
    }

    private void upsertValue(Map<String, SiteConfig> existing, String key, String value, String description, int type) {
        SiteConfig row = existing.get(key);
        if (row == null) {
            row = new SiteConfig();
            row.setConfigKey(key);
            row.setDescription(description);
            row.setConfigType(type);
            row.setIsSystem(1);
            row.setIsPublic(1);
        }
        row.setConfigValue(value);
        if (row.getId() == null) {
            siteConfigMapper.insert(row);
            existing.put(key, row);
        } else {
            siteConfigMapper.updateById(row);
        }
    }

    private void persistHeroImages(SiteConfigDTO config) {
        Map<String, String> heroImages = config.getHeroImages();
        if (heroImages == null || heroImages.isEmpty()) {
            return;
        }
        sysFileService.convertToPermanent(
                heroImages.values().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList(),
                SysFile.RefType.SITE_HERO,
                SITE_CONFIG_REF_ID
        );
    }

    private static SiteConfigDTO mergeWithDefaults(SiteConfigDTO source) {
        SiteConfigDTO merged = defaults();
        applyPatch(merged, source);
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

    private Map<String, String> parseHeroImages(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class)
            );
        } catch (IOException e) {
            log.warn("Failed to parse hero images config", e);
            return new LinkedHashMap<>();
        }
    }

    private String toHeroImagesJson(Map<String, String> heroImages) {
        try {
            return objectMapper.writeValueAsString(heroImages == null ? Map.of() : heroImages);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize hero images config", e);
        }
    }

    private static void applyPatch(SiteConfigDTO target, SiteConfigDTO patch) {
        if (patch == null) {
            return;
        }
        if (patch.getSiteName() != null) {
            target.setSiteName(patch.getSiteName());
        }
        if (patch.getSiteDescription() != null) {
            target.setSiteDescription(patch.getSiteDescription());
        }
        if (patch.getSiteLogo() != null) {
            target.setSiteLogo(patch.getSiteLogo());
        }
        if (patch.getSiteFavicon() != null) {
            target.setSiteFavicon(patch.getSiteFavicon());
        }
        if (patch.getIcp() != null) {
            target.setIcp(patch.getIcp());
        }
        if (patch.getGithub() != null) {
            target.setGithub(patch.getGithub());
        }
        if (patch.getEmail() != null) {
            target.setEmail(patch.getEmail());
        }
        if (patch.getHeroImages() != null) {
            Map<String, String> merged = new LinkedHashMap<>();
            if (target.getHeroImages() != null) {
                merged.putAll(target.getHeroImages());
            }
            for (Map.Entry<String, String> entry : patch.getHeroImages().entrySet()) {
                if (!StringUtils.hasText(entry.getKey())) {
                    continue;
                }
                String key = entry.getKey().trim();
                String value = entry.getValue();
                if (!StringUtils.hasText(value)) {
                    merged.remove(key);
                    continue;
                }
                merged.put(key, value.trim());
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

        Map<String, String> normalizedHeroImages = new LinkedHashMap<>();
        if (config.getHeroImages() != null) {
            for (Map.Entry<String, String> entry : config.getHeroImages().entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                String key = entry.getKey().trim();
                String value = entry.getValue().trim();
                if (!isSafeHeroImageUrl(value)) {
                    continue;
                }
                normalizedHeroImages.put(key, value);
            }
        }
        config.setHeroImages(normalizedHeroImages);
    }

    private static boolean isSafeHeroImageUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String url = value.trim();
        if (url.startsWith("/")) {
            return !containsUnsafeCssChars(url);
        }
        return (url.startsWith("http://") || url.startsWith("https://")) && !containsUnsafeCssChars(url);
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
