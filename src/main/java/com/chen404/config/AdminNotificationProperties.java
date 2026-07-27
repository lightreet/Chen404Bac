package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员消息中心发布开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.admin-notification")
public class AdminNotificationProperties {

    private boolean enabled;
}
