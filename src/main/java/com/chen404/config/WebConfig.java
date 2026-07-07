package com.chen404.config;

import com.chen404.interceptor.LoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:20204,http://127.0.0.1:20204,http://192.168.1.6:20204,http://localhost:5173,http://127.0.0.1:5173,http://192.168.1.6:5173}")
    private String corsAllowedOrigins;

    private String[] parseCorsOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(parseCorsOrigins())
                // 允许的方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // 允许的请求头
                .allowedHeaders("*")
                // 允许携带凭证（cookies）
                .allowCredentials(true)
                // 预检请求缓存时间
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 日志拦截器（最先执行，记录所有请求）
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/uploads/**",
                        "/static/**",
                        "/favicon.ico"
                )
                .order(1);
    }
}
