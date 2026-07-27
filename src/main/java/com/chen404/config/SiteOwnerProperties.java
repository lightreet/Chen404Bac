package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 站点所有者身份配置，避免在业务代码中散落固定用户 ID。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class SiteOwnerProperties {

    private Long siteOwnerUserId = 1L;
}
