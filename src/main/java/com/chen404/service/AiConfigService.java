package com.chen404.service;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiConfigTestRequest;
import com.chen404.domain.dto.AiConfigTestResponse;

/**
 * AI 后台配置服务。
 */
public interface AiConfigService {

    AiAdminConfigDTO getAdminConfig();

    AiAdminConfigDTO updateAdminConfig(AiAdminConfigDTO patch);

    AiAdminConfigDTO getEffectiveConfig();

    AiConfigTestResponse testConnection(AiConfigTestRequest request);
}
