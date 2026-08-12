package com.chen404.service.impl;

import com.chen404.domain.dto.FeatureToggleConfigDTO;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.mapper.SiteConfigMapper;
import com.chen404.service.FeatureToggleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于 {@code site_config} 私有配置项的运行时功能开关实现。
 *
 * <p>读取结果缓存在当前服务实例内，管理后台保存时主动失效缓存，避免权限判断热路径重复查询数据库。</p>
 */
@Slf4j
@Service
public class FeatureToggleServiceImpl implements FeatureToggleService {

    private static final int CONFIG_TYPE_BOOLEAN = 3;
    private static final int SYSTEM_CONFIG = 1;
    private static final int PRIVATE_CONFIG = 0;

    private final SiteConfigMapper siteConfigMapper;
    private volatile Map<RuntimeFeatureEnum, Boolean> cachedFeatures;

    public FeatureToggleServiceImpl(SiteConfigMapper siteConfigMapper) {
        this.siteConfigMapper = siteConfigMapper;
    }

    @Override
    public FeatureToggleConfigDTO getAdminConfig() {
        return toDto(getFeatureSnapshot());
    }

    @Override
    @Transactional
    public synchronized FeatureToggleConfigDTO updateAdminConfig(
            FeatureToggleConfigDTO patch,
            Long operatorId) {
        EnumMap<RuntimeFeatureEnum, Boolean> updated = new EnumMap<>(getFeatureSnapshot());
        applyPatch(updated, patch);
        writeToDatabase(updated);
        refreshCacheAfterCommit(updated);
        log.info("[FEATURE_TOGGLE_UPDATE] operatorId={} articleCreation={} travelCreation={} musicCreation={} "
                        + "adminNotification={} aiArticleAssist={} aiMusicAssist={} aiArticleRecommend={}",
                operatorId,
                updated.get(RuntimeFeatureEnum.ARTICLE_CREATION),
                updated.get(RuntimeFeatureEnum.TRAVEL_CREATION),
                updated.get(RuntimeFeatureEnum.MUSIC_CREATION),
                updated.get(RuntimeFeatureEnum.ADMIN_NOTIFICATION),
                updated.get(RuntimeFeatureEnum.AI_ARTICLE_ASSIST),
                updated.get(RuntimeFeatureEnum.AI_MUSIC_ASSIST),
                updated.get(RuntimeFeatureEnum.AI_ARTICLE_RECOMMEND));
        return toDto(updated);
    }

    /**
     * 事务提交后再替换缓存，避免其他请求在数据库提交前重新缓存旧值。
     */
    private void refreshCacheAfterCommit(Map<RuntimeFeatureEnum, Boolean> updated) {
        Map<RuntimeFeatureEnum, Boolean> snapshot = Map.copyOf(updated);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cachedFeatures = snapshot;
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cachedFeatures = snapshot;
            }
        });
    }

    @Override
    public boolean isEnabled(RuntimeFeatureEnum feature) {
        if (feature == null) {
            return false;
        }
        return getFeatureSnapshot().getOrDefault(feature, feature.isDefaultEnabled());
    }

    @Override
    public List<String> resolveAvailableCapabilities(User user) {
        List<String> capabilities = UserCapabilityEnum.resolveCodes(user);
        if (user != null && UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode())) {
            return capabilities;
        }
        Map<RuntimeFeatureEnum, Boolean> features = getFeatureSnapshot();
        return capabilities.stream()
                .filter(capability -> isCapabilityEnabled(capability, features))
                .toList();
    }

    private Map<RuntimeFeatureEnum, Boolean> getFeatureSnapshot() {
        Map<RuntimeFeatureEnum, Boolean> snapshot = cachedFeatures;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cachedFeatures == null) {
                cachedFeatures = Map.copyOf(loadFeatureSnapshot());
            }
            return cachedFeatures;
        }
    }

    private EnumMap<RuntimeFeatureEnum, Boolean> loadFeatureSnapshot() {
        EnumMap<RuntimeFeatureEnum, Boolean> features = defaults();
        Map<String, String> values = siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        row -> row.getConfigValue() == null ? "" : row.getConfigValue(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (RuntimeFeatureEnum feature : RuntimeFeatureEnum.values()) {
            features.put(feature, parseBoolean(values.get(feature.getConfigKey()), feature.isDefaultEnabled()));
        }
        return features;
    }

    private void writeToDatabase(Map<RuntimeFeatureEnum, Boolean> features) {
        Map<String, SiteConfig> existing = siteConfigMapper.selectAllConfigs().stream()
                .filter(row -> row != null && StringUtils.hasText(row.getConfigKey()))
                .collect(Collectors.toMap(
                        row -> row.getConfigKey().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (RuntimeFeatureEnum feature : RuntimeFeatureEnum.values()) {
            upsertValue(existing, feature, features.getOrDefault(feature, feature.isDefaultEnabled()));
        }
    }

    private void upsertValue(
            Map<String, SiteConfig> existing,
            RuntimeFeatureEnum feature,
            boolean enabled) {
        SiteConfig row = existing.get(feature.getConfigKey());
        if (row == null) {
            row = new SiteConfig();
            row.setConfigKey(feature.getConfigKey());
            row.setDefaultValue(String.valueOf(feature.isDefaultEnabled()));
            row.setDescription(feature.getDescription());
            row.setConfigType(CONFIG_TYPE_BOOLEAN);
            row.setIsSystem(SYSTEM_CONFIG);
        }
        row.setConfigValue(String.valueOf(enabled));
        row.setIsPublic(PRIVATE_CONFIG);
        if (row.getId() == null) {
            siteConfigMapper.insert(row);
            existing.put(feature.getConfigKey(), row);
        } else {
            siteConfigMapper.updateById(row);
        }
    }

    private static EnumMap<RuntimeFeatureEnum, Boolean> defaults() {
        EnumMap<RuntimeFeatureEnum, Boolean> defaults = new EnumMap<>(RuntimeFeatureEnum.class);
        for (RuntimeFeatureEnum feature : RuntimeFeatureEnum.values()) {
            defaults.put(feature, feature.isDefaultEnabled());
        }
        return defaults;
    }

    private static void applyPatch(
            EnumMap<RuntimeFeatureEnum, Boolean> target,
            FeatureToggleConfigDTO patch) {
        if (patch == null) {
            return;
        }
        putIfPresent(target, RuntimeFeatureEnum.ARTICLE_CREATION, patch.getArticleCreationEnabled());
        putIfPresent(target, RuntimeFeatureEnum.TRAVEL_CREATION, patch.getTravelCreationEnabled());
        putIfPresent(target, RuntimeFeatureEnum.MUSIC_CREATION, patch.getMusicCreationEnabled());
        putIfPresent(target, RuntimeFeatureEnum.ADMIN_NOTIFICATION, patch.getAdminNotificationEnabled());
        putIfPresent(target, RuntimeFeatureEnum.AI_ARTICLE_ASSIST, patch.getAiArticleAssistEnabled());
        putIfPresent(target, RuntimeFeatureEnum.AI_MUSIC_ASSIST, patch.getAiMusicAssistEnabled());
        putIfPresent(target, RuntimeFeatureEnum.AI_ARTICLE_RECOMMEND, patch.getAiArticleRecommendEnabled());
    }

    private static void putIfPresent(
            EnumMap<RuntimeFeatureEnum, Boolean> target,
            RuntimeFeatureEnum feature,
            Boolean value) {
        if (value != null) {
            target.put(feature, value);
        }
    }

    private static FeatureToggleConfigDTO toDto(Map<RuntimeFeatureEnum, Boolean> features) {
        FeatureToggleConfigDTO dto = new FeatureToggleConfigDTO();
        dto.setArticleCreationEnabled(features.get(RuntimeFeatureEnum.ARTICLE_CREATION));
        dto.setTravelCreationEnabled(features.get(RuntimeFeatureEnum.TRAVEL_CREATION));
        dto.setMusicCreationEnabled(features.get(RuntimeFeatureEnum.MUSIC_CREATION));
        dto.setAdminNotificationEnabled(features.get(RuntimeFeatureEnum.ADMIN_NOTIFICATION));
        dto.setAiArticleAssistEnabled(features.get(RuntimeFeatureEnum.AI_ARTICLE_ASSIST));
        dto.setAiMusicAssistEnabled(features.get(RuntimeFeatureEnum.AI_MUSIC_ASSIST));
        dto.setAiArticleRecommendEnabled(features.get(RuntimeFeatureEnum.AI_ARTICLE_RECOMMEND));
        return dto;
    }

    private static boolean isCapabilityEnabled(
            String capability,
            Map<RuntimeFeatureEnum, Boolean> features) {
        if (UserCapabilityEnum.ARTICLE_CREATE.getCode().equals(capability)) {
            return features.getOrDefault(
                    RuntimeFeatureEnum.ARTICLE_CREATION,
                    RuntimeFeatureEnum.ARTICLE_CREATION.isDefaultEnabled());
        }
        if (UserCapabilityEnum.TRAVEL_CREATE.getCode().equals(capability)) {
            return features.getOrDefault(
                    RuntimeFeatureEnum.TRAVEL_CREATION,
                    RuntimeFeatureEnum.TRAVEL_CREATION.isDefaultEnabled());
        }
        if (UserCapabilityEnum.MUSIC_CREATE.getCode().equals(capability)) {
            return features.getOrDefault(
                    RuntimeFeatureEnum.MUSIC_CREATION,
                    RuntimeFeatureEnum.MUSIC_CREATION.isDefaultEnabled());
        }
        return true;
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return fallback;
    }
}
