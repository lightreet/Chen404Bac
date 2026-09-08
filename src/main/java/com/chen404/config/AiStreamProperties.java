package com.chen404.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 流式生成的进程级资源预算，与可在后台调整的模型参数分开管理。 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.ai.stream")
public class AiStreamProperties {
    @Min(1)
    private int maxConcurrent = 8;
    @Min(1)
    private int maxPerUser = 2;
    @Min(1)
    private int maxAnonymous = 2;
    @Min(1)
    private long totalTimeoutMs = 120_000;
    @Min(1)
    private long idleTimeoutMs = 30_000;
    @Min(1)
    private int maxResponseChars = 32_768;
}
