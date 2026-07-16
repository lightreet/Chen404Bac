package com.chen404.service.impl;

import com.chen404.config.GitHubDevelopmentProperties;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.mapper.SiteConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubDevelopmentConfigServiceImplTest {

    @Test
    void shouldMaskConfiguredToken() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "development.github.token", "github_pat_abcdef123456"
        ));
        GitHubDevelopmentConfigServiceImpl service = buildService(mapper);

        GitHubDevelopmentAdminConfigDTO config = service.getAdminConfig();

        assertNull(config.getToken());
        assertTrue(config.getTokenConfigured());
        assertEquals("gith****3456", config.getTokenPreview());
    }

    @Test
    void shouldPreserveExistingTokenWhenPatchTokenIsBlank() {
        Map<String, SiteConfig> rows = seedRows(Map.of(
                "development.github.token", "github_pat_old_secret"
        ));
        GitHubDevelopmentConfigServiceImpl service = buildService(mapperWithRows(rows));
        GitHubDevelopmentAdminConfigDTO patch = service.getAdminConfig();
        patch.setOwner("chen404-owner");
        patch.setToken("");

        service.updateAdminConfig(patch);

        assertEquals("github_pat_old_secret", value(rows, "development.github.token"));
        assertEquals("chen404-owner", value(rows, "development.github.owner"));
    }

    @Test
    void shouldClearTokenExplicitly() {
        Map<String, SiteConfig> rows = seedRows(Map.of(
                "development.github.token", "github_pat_old_secret"
        ));
        GitHubDevelopmentConfigServiceImpl service = buildService(mapperWithRows(rows));
        GitHubDevelopmentAdminConfigDTO patch = service.getAdminConfig();
        patch.setClearToken(true);

        GitHubDevelopmentAdminConfigDTO saved = service.updateAdminConfig(patch);

        assertEquals("", value(rows, "development.github.token"));
        assertFalse(saved.getTokenConfigured());
    }

    @Test
    void shouldUseDatabaseValuesAndClampNumericSettings() {
        SiteConfigMapper mapper = mapperWithSeed(Map.of(
                "development.github.repositories", "[\"RepoOne\",\"RepoTwo\"]",
                "development.github.cache_minutes", "0",
                "development.github.api_commit_limit", "999",
                "development.github.request_timeout_seconds", "1"
        ));
        GitHubDevelopmentConfigServiceImpl service = buildService(mapper);

        GitHubDevelopmentAdminConfigDTO config = service.getEffectiveConfig();

        assertEquals(List.of("RepoOne", "RepoTwo"), config.getRepositories());
        assertEquals(1, config.getCacheMinutes());
        assertEquals(100, config.getApiCommitLimit());
        assertEquals(3, config.getRequestTimeoutSeconds());
    }

    private GitHubDevelopmentConfigServiceImpl buildService(SiteConfigMapper mapper) {
        GitHubDevelopmentProperties properties = new GitHubDevelopmentProperties();
        properties.setOwner("lightreet");
        properties.setRepositories(List.of("Chen404Fro", "Chen404Bac"));
        properties.setBranch("main");
        properties.setToken("");
        return new GitHubDevelopmentConfigServiceImpl(mapper, properties);
    }

    private SiteConfigMapper mapperWithSeed(Map<String, String> seed) {
        return mapperWithRows(seedRows(seed));
    }

    private SiteConfigMapper mapperWithRows(Map<String, SiteConfig> rows) {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        when(mapper.selectAllConfigs()).thenAnswer(invocation -> new ArrayList<>(rows.values()));
        when(mapper.insert(any(SiteConfig.class))).thenAnswer(invocation -> {
            SiteConfig row = invocation.getArgument(0);
            row.setId((long) rows.size() + 1);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(mapper.updateById(any(SiteConfig.class))).thenAnswer(invocation -> {
            SiteConfig row = invocation.getArgument(0);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        return mapper;
    }

    private Map<String, SiteConfig> seedRows(Map<String, String> seed) {
        Map<String, SiteConfig> rows = new LinkedHashMap<>();
        long[] nextId = {1L};
        seed.forEach((key, value) -> {
            SiteConfig row = new SiteConfig();
            row.setId(nextId[0]++);
            row.setConfigKey(key);
            row.setConfigValue(value);
            rows.put(key, row);
        });
        return rows;
    }

    private String value(Map<String, SiteConfig> rows, String key) {
        SiteConfig row = rows.get(key);
        return row == null ? null : row.getConfigValue();
    }
}
