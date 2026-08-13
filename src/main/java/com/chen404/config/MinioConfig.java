package com.chen404.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

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
     * 需要经过业务鉴权才能读取的文件存储桶。
     */
    private String protectedBucketName = "chen404-protected";

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
        return getFileUrl(bucketName, objectName);
    }

    public String getFileUrl(String targetBucketName, String objectName) {
        String baseUrl = StringUtils.hasText(externalUrl) ? externalUrl : endpoint;
        return baseUrl + "/" + targetBucketName + "/" + objectName;
    }

    /**
     * 将使用内网 MinIO 地址生成的预签名 URL 转换为浏览器可访问地址。
     *
     * <p>签名参数和对象路径必须保持原样，仅替换访问入口。这样服务端仍可通过
     * {@link #endpoint} 访问 MinIO，浏览器则经由 {@link #externalUrl} 对应的反向代理读取文件。</p>
     */
    public String externalizePresignedUrl(String presignedUrl) {
        if (!StringUtils.hasText(externalUrl) || !StringUtils.hasText(presignedUrl)) {
            return presignedUrl;
        }

        URI signedUri = URI.create(presignedUrl);
        String baseUrl = externalUrl.trim().replaceFirst("/+$", "");
        StringBuilder externalizedUrl = new StringBuilder(baseUrl)
                .append(signedUri.getRawPath());
        if (StringUtils.hasText(signedUri.getRawQuery())) {
            externalizedUrl.append('?').append(signedUri.getRawQuery());
        }
        return externalizedUrl.toString();
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
        if (!StringUtils.hasText(protectedBucketName)) {
            throw new IllegalStateException("MinIO protectedBucketName 未配置");
        }
    }
}
