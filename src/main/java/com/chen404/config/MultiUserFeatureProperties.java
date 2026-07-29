package com.chen404.config;

import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.entity.User;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多用户创作能力的发布开关。
 *
 * <p>开关用于控制知友共创的灰度发布；启用中的管理员始终保留全部创作能力，
 * 以便在功能回退期间继续维护和修复站点内容。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.multi-user")
public class MultiUserFeatureProperties {

    private boolean articleCreationEnabled;
    private boolean travelCreationEnabled;
    private boolean musicCreationEnabled;

    /**
     * 解析当前用户在发布开关约束下可用的能力。
     *
     * @param user 已补齐角色、信任级别和状态的用户
     * @return 当前可用能力编码
     */
    public List<String> resolveAvailableCapabilities(User user) {
        List<String> capabilities = UserCapabilityEnum.resolveCodes(user);
        if (user != null && UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode())) {
            return capabilities;
        }
        return filterEnabledCapabilities(capabilities);
    }

    public List<String> filterEnabledCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        return capabilities.stream()
                .filter(this::isCapabilityEnabled)
                .toList();
    }

    private boolean isCapabilityEnabled(String capability) {
        if (UserCapabilityEnum.ARTICLE_CREATE.getCode().equals(capability)) {
            return articleCreationEnabled;
        }
        if (UserCapabilityEnum.TRAVEL_CREATE.getCode().equals(capability)) {
            return travelCreationEnabled;
        }
        if (UserCapabilityEnum.MUSIC_CREATE.getCode().equals(capability)) {
            return musicCreationEnabled;
        }
        return true;
    }
}
