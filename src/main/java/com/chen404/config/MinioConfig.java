package com.chen404.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /**
     * MinIO 服务地址
     */
    private String endpoint = "http://192.168.1.6:9000";

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 密钥
     */
    private String secretKey;

    /**
     * 存储桶名称
     */
    private String bucketName = "chen404";

    /**
     * 外部访问URL（如果通过CDN或域名访问）
     * 为空则使用 endpoint
     */
    private String externalUrl = "";

    @Bean
    public MinioClient minioClient() {
        validateRequiredConfig();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 获取文件访问URL
     */
    public String getFileUrl(String objectName) {
        String baseUrl = StringUtils.hasText(externalUrl) ? externalUrl : endpoint;
        return baseUrl + "/" + bucketName + "/" + objectName;
    }

    private void validateRequiredConfig() {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("MinIO endpoint 未配置");
        }
        if (!StringUtils.hasText(accessKey)) {
            throw new IllegalStateException("MinIO accessKey 未配置");
        }
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("MinIO secretKey 未配置");
        }
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalStateException("MinIO bucketName 未配置");
        }
    }
}
