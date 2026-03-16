package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /**
     * 临时文件过期时间（小时）
     */
    private int tempFileExpireHours = 24;

    /**
     * 最大图片大小（MB）
     */
    private int maxImageSizeMb = 12;

    /**
     * 批量上传最大文件数
     */
    private int maxBatchUploadCount = 10;
}
