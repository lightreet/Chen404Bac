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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SiteConfigServiceImpl implements SiteConfigService {

    private static final long SITE_CONFIG_REF_ID = 1L;
    private static final String DEFAULT_GITHUB_URL = "https://github.com/lightreet";
    private static final String LEGACY_GITHUB_URL = "https://github.com/chen404";
    private static final String DEFAULT_SITE_EMAIL = "helychen@outlook.com";
    private static final String LEGACY_SITE_EMAIL = "admin@chen404.com";
    private static final String DEFAULT_SITE_LOGO = "/logo.png";
    private static final String LEGACY_SITE_LOGO = "/logo.svg";
    private static final String DEFAULT_SITE_FAVICON = "/favicon.png";
    private static final String LEGACY_SITE_FAVICON = "/favicon.ico";

    private static final String KEY_SITE_NAME = "site.name";
    private static final String KEY_SITE_DESCRIPTION = "site.description";
    private static final String KEY_SITE_LOGO = "site.logo";
    private static final String KEY_SITE_FAVICON = "site.favicon";
    private static final String KEY_SITE_ICP = "site.icp";
    private static final String KEY_SITE_BEIAN = "site.beian";
    private static final String KEY_SITE_GITHUB = "site.github";
    private static final String KEY_SITE_EMAIL = "site.email";
    private static final String KEY_SITE_COPYRIGHT = "site.copyright";
    private static final String KEY_SEO_KEYWORDS = "seo.keywords";
    private static final String KEY_SEO_DESCRIPTION = "seo.description";
    private static final String KEY_COMMENT_AUDIT = "comment.audit";
    private static final String KEY_COMMENT_GUEST = "comment.guest";
    private static final String KEY_HERO_IMAGES = "site.hero_images";
    private static final String KEY_HERO_IMAGE_POSITIONS = "site.hero_image_positions";
    private static final Pattern HERO_POSITION_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3}(?:\\.\\d+)?)%\\s+(\\d{1,3}(?:\\.\\d+)?)%\\s*$"
    );

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
        persistSiteAssets(current);
        persistHeroImages(current);
        return current;
    }

    private SiteConfigDTO loadFromDatabase() {
        List<SiteConfig> rows = siteConfigMapper.selectAllConfigs();
        if (rows == null || rows.isEmpty()) {
            SiteConfigDTO config = defaults();
            writeToDatabase(config);
            persistSiteAssets(config);
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
                case KEY_SITE_BEIAN -> dto.setBeian(value);
                case KEY_SITE_GITHUB -> dto.setGithub(value);
                case KEY_SITE_EMAIL -> dto.setEmail(value);
                case KEY_SITE_COPYRIGHT -> dto.setCopyright(value);
                case KEY_SEO_KEYWORDS -> dto.setSeoKeywords(value);
                case KEY_SEO_DESCRIPTION -> dto.setSeoDescription(value);
                case KEY_COMMENT_AUDIT -> dto.setCommentAudit(parseBoolean(value));
                case KEY_COMMENT_GUEST -> dto.setCommentGuest(parseBoolean(value));
                case KEY_HERO_IMAGES -> dto.setHeroImages(parseHeroImages(value));
                case KEY_HERO_IMAGE_POSITIONS -> dto.setHeroImagePositions(parseHeroImagePositions(value));
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
        upsertValue(existing, KEY_SITE_BEIAN, config.getBeian(), "Police filing number", 1);
        upsertValue(existing, KEY_SITE_GITHUB, config.getGithub(), "GitHub link", 1);
        upsertValue(existing, KEY_SITE_EMAIL, config.getEmail(), "Contact email", 1);
        upsertValue(existing, KEY_SITE_COPYRIGHT, config.getCopyright(), "Copyright text", 1);
        upsertValue(existing, KEY_SEO_KEYWORDS, config.getSeoKeywords(), "SEO keywords", 1);
        upsertValue(existing, KEY_SEO_DESCRIPTION, config.getSeoDescription(), "SEO description", 1);
        upsertValue(existing, KEY_COMMENT_AUDIT, String.valueOf(Boolean.TRUE.equals(config.getCommentAudit())), "Comment audit enabled", 3);
        upsertValue(existing, KEY_COMMENT_GUEST, String.valueOf(Boolean.TRUE.equals(config.getCommentGuest())), "Guest comment enabled", 3);
        upsertValue(existing, KEY_HERO_IMAGES, toHeroImagesJson(config.getHeroImages()), "Hero images", 4);
        upsertValue(existing, KEY_HERO_IMAGE_POSITIONS, toHeroImagePositionsJson(config.getHeroImagePositions()), "Hero image positions", 4);
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

    private void persistSiteAssets(SiteConfigDTO config) {
        sysFileService.convertToPermanent(
                List.of(config.getSiteLogo(), config.getSiteFavicon()).stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList(),
                SysFile.RefType.SITE_ASSET,
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
        config.setSiteDescription("一个写下技术，也收藏温柔日常的小小角落");
        config.setSiteLogo(DEFAULT_SITE_LOGO);
        config.setSiteFavicon(DEFAULT_SITE_FAVICON);
        config.setIcp("湘ICP备2026010852号-1");
        config.setBeian("");
        config.setGithub(DEFAULT_GITHUB_URL);
        config.setEmail(DEFAULT_SITE_EMAIL);
        config.setCopyright("Copyright 2024 Chen404");
        config.setSeoKeywords("博客,技术,前端,后端,Java,Vue");
        config.setSeoDescription("Chen404的个人技术博客，一个写下技术，也收藏温柔日常的小小角落");
        config.setCommentAudit(true);
        config.setCommentGuest(true);
        config.setHeroImages(new LinkedHashMap<>());
        config.setHeroImagePositions(new LinkedHashMap<>());
        return config;
    }

    private Map<String, String> parseHeroImages(String value) {
        return parseStringMap(value, "Failed to parse hero images config");
    }

    private Map<String, String> parseHeroImagePositions(String value) {
        return parseStringMap(value, "Failed to parse hero image positions config");
    }

    private Map<String, String> parseStringMap(String value, String logMessage) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class)
            );
        } catch (IOException e) {
            log.warn(logMessage, e);
            return new LinkedHashMap<>();
        }
    }

    private String toHeroImagesJson(Map<String, String> heroImages) {
        return toStringMapJson(heroImages, "Failed to serialize hero images config");
    }

    private String toHeroImagePositionsJson(Map<String, String> heroImagePositions) {
        return toStringMapJson(heroImagePositions, "Failed to serialize hero image positions config");
    }

    private String toStringMapJson(Map<String, String> value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(errorMessage, e);
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
        if (patch.getBeian() != null) {
            target.setBeian(patch.getBeian());
        }
        if (patch.getGithub() != null) {
            target.setGithub(patch.getGithub());
        }
        if (patch.getEmail() != null) {
            target.setEmail(patch.getEmail());
        }
        if (patch.getCopyright() != null) {
            target.setCopyright(patch.getCopyright());
        }
        if (patch.getSeoKeywords() != null) {
            target.setSeoKeywords(patch.getSeoKeywords());
        }
        if (patch.getSeoDescription() != null) {
            target.setSeoDescription(patch.getSeoDescription());
        }
        if (patch.getCommentAudit() != null) {
            target.setCommentAudit(patch.getCommentAudit());
        }
        if (patch.getCommentGuest() != null) {
            target.setCommentGuest(patch.getCommentGuest());
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
        if (patch.getHeroImagePositions() != null) {
            Map<String, String> merged = new LinkedHashMap<>();
            if (target.getHeroImagePositions() != null) {
                merged.putAll(target.getHeroImagePositions());
            }
            for (Map.Entry<String, String> entry : patch.getHeroImagePositions().entrySet()) {
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
            target.setHeroImagePositions(merged);
        }
    }

    private static void normalize(SiteConfigDTO config) {
        config.setSiteName(trimToDefault(config.getSiteName(), "Chen404 Blog"));
        config.setSiteDescription(trimToDefault(config.getSiteDescription(), "一个写下技术，也收藏温柔日常的小小角落"));
        config.setSiteLogo(normalizeSiteLogo(config.getSiteLogo()));
        config.setSiteFavicon(normalizeSiteFavicon(config.getSiteFavicon()));
        config.setIcp(trimToDefault(config.getIcp(), "湘ICP备2026010852号-1"));
        config.setBeian(trimToEmpty(config.getBeian()));
        config.setGithub(normalizeGithub(config.getGithub()));
        config.setEmail(normalizeEmail(config.getEmail()));
        config.setCopyright(trimToDefault(config.getCopyright(), "Copyright 2024 Chen404"));
        config.setSeoKeywords(trimToEmpty(config.getSeoKeywords()));
        config.setSeoDescription(trimToDefault(
                config.getSeoDescription(),
                "Chen404的个人技术博客，一个写下技术，也收藏温柔日常的小小角落"
        ));
        config.setCommentAudit(config.getCommentAudit() == null || config.getCommentAudit());
        config.setCommentGuest(config.getCommentGuest() == null || config.getCommentGuest());

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
        if (!normalizedHeroImages.containsKey("tag")) {
            String tagFallback = normalizedHeroImages.getOrDefault("category", normalizedHeroImages.get("home"));
            if (StringUtils.hasText(tagFallback)) {
                normalizedHeroImages.put("tag", tagFallback);
            }
        }
        config.setHeroImages(normalizedHeroImages);

        Map<String, String> normalizedHeroImagePositions = new LinkedHashMap<>();
        if (config.getHeroImagePositions() != null) {
            for (Map.Entry<String, String> entry : config.getHeroImagePositions().entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                String key = entry.getKey().trim();
                String position = normalizeHeroImagePosition(entry.getValue());
                if (!StringUtils.hasText(position)) {
                    continue;
                }
                normalizedHeroImagePositions.put(key, position);
            }
        }
        if (!normalizedHeroImagePositions.containsKey("tag")) {
            String tagFallback = normalizedHeroImagePositions.getOrDefault(
                    "category",
                    normalizedHeroImagePositions.get("home")
            );
            if (StringUtils.hasText(tagFallback)) {
                normalizedHeroImagePositions.put("tag", tagFallback);
            }
        }
        config.setHeroImagePositions(normalizedHeroImagePositions);
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

    private static String normalizeHeroImagePosition(String value) {
        if (!StringUtils.hasText(value) || containsUnsafeCssChars(value)) {
            return "";
        }
        Matcher matcher = HERO_POSITION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return "";
        }
        double x = clampPercent(Double.parseDouble(matcher.group(1)));
        double y = clampPercent(Double.parseDouble(matcher.group(2)));
        return formatPercent(x) + " " + formatPercent(y);
    }

    private static double clampPercent(double value) {
        return Math.max(0D, Math.min(100D, value));
    }

    private static String formatPercent(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        if (rounded == Math.rint(rounded)) {
            return (long) rounded + "%";
        }
        return rounded + "%";
    }

    private static String trimToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String normalizeGithub(String value) {
        String normalized = trimToDefault(value, DEFAULT_GITHUB_URL);
        return LEGACY_GITHUB_URL.equalsIgnoreCase(normalized) ? DEFAULT_GITHUB_URL : normalized;
    }

    private static String normalizeEmail(String value) {
        String normalized = trimToDefault(value, DEFAULT_SITE_EMAIL);
        return LEGACY_SITE_EMAIL.equalsIgnoreCase(normalized) ? DEFAULT_SITE_EMAIL : normalized;
    }

    private static String normalizeSiteLogo(String value) {
        String normalized = trimToDefault(value, DEFAULT_SITE_LOGO);
        return LEGACY_SITE_LOGO.equalsIgnoreCase(normalized) ? DEFAULT_SITE_LOGO : normalized;
    }

    private static String normalizeSiteFavicon(String value) {
        String normalized = trimToDefault(value, DEFAULT_SITE_FAVICON);
        return LEGACY_SITE_FAVICON.equalsIgnoreCase(normalized) ? DEFAULT_SITE_FAVICON : normalized;
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

}
