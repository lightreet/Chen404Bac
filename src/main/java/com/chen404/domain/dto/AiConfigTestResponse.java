package com.chen404.domain.dto;

import lombok.Data;

/**
 * 后台 AI 连接测试响应。
 */
@Data
public class AiConfigTestResponse {

    private Boolean success;
    private String message;
    private String sampleText;
    private String traceId;
    private Long latencyMs;
}
