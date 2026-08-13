package com.chen404.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinioConfigTest {

    @Test
    void shouldRejectMissingCredentials() {
        MinioConfig config = new MinioConfig();

        assertThrows(IllegalStateException.class, config::minioClient);
    }

    @Test
    void shouldUseExternalUrlWhenBuildingFileUrl() {
        MinioConfig config = new MinioConfig();
        config.setEndpoint("http://minio:9000");
        config.setBucketName("chen404");
        config.setExternalUrl("https://cdn.example.com");

        assertEquals("https://cdn.example.com/chen404/articles/a.png", config.getFileUrl("articles/a.png"));
    }

    @Test
    void shouldExternalizePresignedUrlWithoutChangingPathOrSignature() {
        MinioConfig config = new MinioConfig();
        config.setExternalUrl("https://www.chen404.cn/minio/");
        String presignedUrl = "http://127.0.0.1:9000/chen404-protected/reader/cover.png"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=a%2Bb";

        assertEquals(
                "https://www.chen404.cn/minio/chen404-protected/reader/cover.png"
                        + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=a%2Bb",
                config.externalizePresignedUrl(presignedUrl));
    }

    @Test
    void shouldKeepInternalPresignedUrlWhenExternalUrlIsMissing() {
        MinioConfig config = new MinioConfig();
        String presignedUrl = "http://minio:9000/chen404-protected/reader/cover.png?signature=abc";

        assertEquals(presignedUrl, config.externalizePresignedUrl(presignedUrl));
    }
}
