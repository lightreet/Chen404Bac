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
}
