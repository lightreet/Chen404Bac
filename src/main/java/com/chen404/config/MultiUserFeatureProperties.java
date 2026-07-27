package com.chen404.config;

import com.chen404.domain.enums.UserCapabilityEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多用户创作能力的发布开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.multi-user")
public class MultiUserFeatureProperties {

    private boolean articleCreationEnabled;
    private boolean travelCreationEnabled;
    private boolean musicCreationEnabled;

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
