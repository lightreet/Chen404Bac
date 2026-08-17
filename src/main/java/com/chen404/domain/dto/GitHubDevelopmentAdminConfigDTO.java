package com.chen404.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台 GitHub 开发同步配置。
 * <p>
 * Token 允许管理员写入，但读取时必须由服务层清空明文并返回脱敏状态。
 */
@Data
public class GitHubDevelopmentAdminConfigDTO {

    private String owner;
    private List<String> repositories = new ArrayList<>();
    private String branch;
    private String token;
    private Boolean tokenConfigured;
    private String tokenPreview;
    private Boolean clearToken;
    private Integer cacheMinutes;
    private Integer requestTimeoutSeconds;
    private String apiBaseUrl;
    private String webBaseUrl;
}
