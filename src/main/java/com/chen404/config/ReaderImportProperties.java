package com.chen404.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 阅读器任务调度、租约与失败重试边界；各执行组件共享配置。 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.reader-import")
public class ReaderImportProperties {
    @Min(1)
    private int batchSize = 32;
    @Min(1000)
    private long pollDelayMillis = 15_000;
    @Min(30)
    private int leaseSeconds = 300;
    @Min(1)
    private int maxAttempts = 3;
    @Min(1)
    private int retryDelaySeconds = 30;
}
