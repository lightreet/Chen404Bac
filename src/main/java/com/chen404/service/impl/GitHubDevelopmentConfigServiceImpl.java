package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chen404.config.GitHubDevelopmentProperties;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.GitHubDevelopmentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库驱动的 GitHub 开发同步配置服务。
 */
@Slf4j
@Service
public class GitHubDevelopmentConfigServiceImpl implements GitHubDevelopmentConfigService {

    private static final String KEY_OWNER = "development.github.owner";
    private static final String KEY_REPOSITORIES = "development.github.repositories";
    private static final String KEY_BRANCH = "development.github.branch";
    private static final String KEY_TOKEN = "development.github.token";
    private static final String KEY_CACHE_MINUTES = "development.github.cache_minutes";
    private static final String KEY_API_COMMIT_LIMIT = "development.github.api_commit_limit";
    private static final String KEY_REQUEST_TIMEOUT_SECONDS = "development.github.request_timeout_seconds";
    private static final String KEY_API_BASE_URL = "development.github.api_base_url";
    private static final String KEY_WEB_BASE_URL = "development.github.web_base_url";

    private static final int MIN_CACHE_MINUTES = 1;
    private static final int MAX_CACHE_MINUTES = 1440;
    private static final int MIN_API_COMMIT_LIMIT = 1;
    private static final int MAX_API_COMMIT_LIMIT = 100;
    private static final int MIN_TIMEOUT_SECONDS = 3;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final SiteConfigMapper siteConfigMapper;
    private final GitHubDevelopmentProperties properties;

    public GitHubDevelopmentConfigServiceImpl(
            SiteConfigMapper siteConfigMapper,
            GitHubDevelopmentProperties properties) {
        this.siteConfigMapper = siteConfigMapper;
        this.properties = properties;
    }

    @Override
    public GitHubDevelopmentAdminConfigDTO getAdminConfig() {
        return sanitizeForAdmin(getEffectiveConfig());
    }

    @Override
    public GitHubDevelopmentAdminConfigDTO updateAdminConfig(GitHubDevelopmentAdminConfigDTO patch) {
        GitHubDevelopmentAdminConfigDTO current = getEffectiveConfig();
        GitHubDevelopmentAdminConfigDTO next = merge(current, patch);
        normalize(next, current.getToken());
        writeToDatabase(next);
        log.info("[GITHUB_CONFIG_UPDATE] owner={} repositories={} tokenConfigured={}",
                next.getOwner(), next.getRepositories().size(), StringUtils.hasText(next.getToken()));
        return sanitizeForAdmin(getEffectiveConfig());
    }

    @Override
    public GitHubDevelopmentAdminConfigDTO getEffectiveConfig() {
        GitHubDevelopmentAdminConfigDTO config = defaults();
        applyRows(config, loadConfigValues());
        normalize(config, config.getToken());
        return config;
    }

    private GitHubDevelopmentAdminConfigDTO defaults() {
        GitHubDevelopmentAdminConfigDTO config = new GitHubDevelopmentAdminConfigDTO();
        config.setOwner(defaultText(properties.getOwner(), "lightreet"));
        config.setRepositories(properties.getRepositories() == null
                ? new ArrayList<>()
                : new ArrayList<>(properties.getRepositories()));
        config.setBranch(defaultText(properties.getBranch(), "main"));
        config.setToken(defaultText(properties.getToken(), ""));
        config.setCacheMinutes(properties.getCacheMinutes());
        config.setApiCommitLimit(properties.getApiCommitLimit());
        config.setRequestTimeoutSeconds(properties.getRequestTimeoutSeconds());
        config.setApiBaseUrl(defaultText(properties.getApiBaseUrl(), "https://api.github.com"));
        config.setWebBaseUrl(defaultText(properties.getWebBaseUrl(), "https://github.com"));
        return config;
    }

    private Map<String, String> loadConfigValues() {
        return siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        row -> row.getConfigValue() == null ? "" : row.getConfigValue(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void applyRows(GitHubDevelopmentAdminConfigDTO config, Map<String, String> rows) {
        config.setOwner(textOrDefault(rows.get(KEY_OWNER), config.getOwner()));
        config.setRepositories(parseRepositories(rows.get(KEY_REPOSITORIES), config.getRepositories()));
        config.setBranch(textOrDefault(rows.get(KEY_BRANCH), config.getBranch()));
        if (rows.containsKey(KEY_TOKEN)) {
            config.setToken(trimToEmpty(rows.get(KEY_TOKEN)));
        }
        config.setCacheMinutes(parseInt(rows.get(KEY_CACHE_MINUTES), config.getCacheMinutes()));
        config.setApiCommitLimit(parseInt(rows.get(KEY_API_COMMIT_LIMIT), config.getApiCommitLimit()));
        config.setRequestTimeoutSeconds(parseInt(
                rows.get(KEY_REQUEST_TIMEOUT_SECONDS), config.getRequestTimeoutSeconds()));
        config.setApiBaseUrl(textOrDefault(rows.get(KEY_API_BASE_URL), config.getApiBaseUrl()));
        config.setWebBaseUrl(textOrDefault(rows.get(KEY_WEB_BASE_URL), config.getWebBaseUrl()));
    }

    private GitHubDevelopmentAdminConfigDTO merge(
            GitHubDevelopmentAdminConfigDTO current,
            GitHubDevelopmentAdminConfigDTO patch) {
        if (patch == null) {
            return current;
        }
        GitHubDevelopmentAdminConfigDTO next = new GitHubDevelopmentAdminConfigDTO();
        next.setOwner(patch.getOwner() == null ? current.getOwner() : patch.getOwner());
        next.setRepositories(patch.getRepositories() == null
                ? new ArrayList<>(current.getRepositories())
                : patch.getRepositories());
        next.setBranch(patch.getBranch() == null ? current.getBranch() : patch.getBranch());
        next.setToken(patch.getToken());
        next.setClearToken(patch.getClearToken());
        next.setCacheMinutes(patch.getCacheMinutes() == null
                ? current.getCacheMinutes()
                : patch.getCacheMinutes());
        next.setApiCommitLimit(patch.getApiCommitLimit() == null
                ? current.getApiCommitLimit()
                : patch.getApiCommitLimit());
        next.setRequestTimeoutSeconds(patch.getRequestTimeoutSeconds() == null
                ? current.getRequestTimeoutSeconds()
                : patch.getRequestTimeoutSeconds());
        next.setApiBaseUrl(patch.getApiBaseUrl() == null
                ? current.getApiBaseUrl()
                : patch.getApiBaseUrl());
        next.setWebBaseUrl(patch.getWebBaseUrl() == null
                ? current.getWebBaseUrl()
                : patch.getWebBaseUrl());
        return next;
    }

    private void normalize(GitHubDevelopmentAdminConfigDTO config, String currentToken) {
        config.setOwner(defaultText(config.getOwner(), "lightreet"));
        config.setRepositories(normalizeRepositories(config.getRepositories()));
        config.setBranch(defaultText(config.getBranch(), "main"));

        if (Boolean.TRUE.equals(config.getClearToken())) {
            config.setToken("");
        } else if (StringUtils.hasText(config.getToken())) {
            config.setToken(config.getToken().trim());
        } else {
            config.setToken(defaultText(currentToken, ""));
        }

        config.setCacheMinutes(clampInt(
                config.getCacheMinutes(), properties.getCacheMinutes(), MIN_CACHE_MINUTES, MAX_CACHE_MINUTES));
        config.setApiCommitLimit(clampInt(
                config.getApiCommitLimit(), properties.getApiCommitLimit(),
                MIN_API_COMMIT_LIMIT, MAX_API_COMMIT_LIMIT));
        config.setRequestTimeoutSeconds(clampInt(
                config.getRequestTimeoutSeconds(), properties.getRequestTimeoutSeconds(),
                MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS));
        config.setApiBaseUrl(defaultText(config.getApiBaseUrl(), "https://api.github.com"));
        config.setWebBaseUrl(defaultText(config.getWebBaseUrl(), "https://github.com"));
        applyTokenStatus(config);
    }

    private List<String> normalizeRepositories(List<String> repositories) {
        if (repositories == null) {
            return List.of();
        }
        return repositories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> parseRepositories(String value, List<String> fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback == null ? List.of() : new ArrayList<>(fallback);
        }
        try {
            List<String> parsed = JSON.parseArray(value, String.class);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException ex) {
            log.warn("[GITHUB_CONFIG_REPOSITORIES_INVALID] fallback=properties message={}", ex.getMessage());
            return fallback == null ? List.of() : new ArrayList<>(fallback);
        }
    }

    private void writeToDatabase(GitHubDevelopmentAdminConfigDTO config) {
        Map<String, SiteConfig> existing = siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        upsertValue(existing, KEY_OWNER, config.getOwner(), "GitHub owner", 1);
        upsertValue(existing, KEY_REPOSITORIES, JSON.toJSONString(config.getRepositories()),
                "GitHub repositories", 1);
        upsertValue(existing, KEY_BRANCH, config.getBranch(), "GitHub branch", 1);
        upsertValue(existing, KEY_TOKEN, config.getToken(), "GitHub access token", 1);
        upsertValue(existing, KEY_CACHE_MINUTES, String.valueOf(config.getCacheMinutes()),
                "GitHub history cache minutes", 2);
        upsertValue(existing, KEY_API_COMMIT_LIMIT, String.valueOf(config.getApiCommitLimit()),
                "GitHub API commit limit", 2);
        upsertValue(existing, KEY_REQUEST_TIMEOUT_SECONDS, String.valueOf(config.getRequestTimeoutSeconds()),
                "GitHub request timeout seconds", 2);
        upsertValue(existing, KEY_API_BASE_URL, config.getApiBaseUrl(), "GitHub API base URL", 1);
        upsertValue(existing, KEY_WEB_BASE_URL, config.getWebBaseUrl(), "GitHub web base URL", 1);
    }

    private void upsertValue(Map<String, SiteConfig> existing, String key, String value, String description, int type) {
        SiteConfig row = existing.get(key);
        if (row == null) {
            row = new SiteConfig();
            row.setConfigKey(key);
            row.setDescription(description);
            row.setConfigType(type);
            row.setIsSystem(1);
            row.setIsPublic(0);
        }
        row.setConfigValue(value);
        row.setIsPublic(0);
        if (row.getId() == null) {
            siteConfigMapper.insert(row);
            existing.put(key, row);
        } else {
            siteConfigMapper.updateById(row);
        }
    }

    private GitHubDevelopmentAdminConfigDTO sanitizeForAdmin(GitHubDevelopmentAdminConfigDTO config) {
        applyTokenStatus(config);
        config.setToken(null);
        config.setClearToken(false);
        return config;
    }

    private void applyTokenStatus(GitHubDevelopmentAdminConfigDTO config) {
        String token = config.getToken();
        config.setTokenConfigured(StringUtils.hasText(token));
        config.setTokenPreview(maskSecret(token));
    }

    private static String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static int clampInt(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private static Integer parseInt(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
