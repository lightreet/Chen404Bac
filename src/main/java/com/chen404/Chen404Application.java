package com.chen404;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import com.chen404.config.ImageProcessingProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(ImageProcessingProperties.class)
@MapperScan("com.chen404.mapper")
@ServletComponentScan("com.chen404.filter")
@EnableAsync
public class Chen404Application {

    public static void main(String[] args) {
        var context = SpringApplication.run(Chen404Application.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;
        log.info("Chen404 Backend 启动成功 | 本地访问: {} | API 文档: {}/swagger-ui.html", baseUrl, baseUrl);
    }
}
