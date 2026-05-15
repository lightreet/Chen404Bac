package com.chen404.service;

import com.chen404.domain.dto.SiteConfigDTO;

/**
 * 站点配置服务，负责读取与更新前台站点基础配置。
 */
public interface SiteConfigService {

    /**
     * 读取站点配置，并补齐默认值。
     *
     * @return 归一化后的站点配置
     */
    SiteConfigDTO getConfig();

    /**
     * 更新站点配置，支持局部补丁写入。
     *
     * @param patch 本次要更新的配置片段
     * @return 更新并归一化后的完整站点配置
     */
    SiteConfigDTO updateConfig(SiteConfigDTO patch);
}
