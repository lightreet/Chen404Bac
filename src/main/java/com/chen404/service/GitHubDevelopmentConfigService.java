package com.chen404.service;

import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;

/**
 * GitHub 开发同步配置服务。
 */
public interface GitHubDevelopmentConfigService {

    /**
     * 获取管理员可见的脱敏配置。
     *
     * @return 不包含明文 Token 的配置
     */
    GitHubDevelopmentAdminConfigDTO getAdminConfig();

    /**
     * 更新数据库配置；空 Token 保留当前值，clearToken 为 true 时显式清除。
     *
     * @param patch 管理员提交的配置
     * @return 保存后的脱敏配置
     */
    GitHubDevelopmentAdminConfigDTO updateAdminConfig(GitHubDevelopmentAdminConfigDTO patch);

    /**
     * 获取同步服务实际使用的完整配置。
     *
     * @return 包含服务端 Token 的生效配置，不得直接返回给前端
     */
    GitHubDevelopmentAdminConfigDTO getEffectiveConfig();
}
