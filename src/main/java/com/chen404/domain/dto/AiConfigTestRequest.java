package com.chen404.domain.dto;

import lombok.Data;

/**
 * 后台 AI 连接测试请求。
 */
@Data
public class AiConfigTestRequest {

    private String message;
    private Boolean useUnsavedConfig;
    private AiAdminConfigDTO config;
}
