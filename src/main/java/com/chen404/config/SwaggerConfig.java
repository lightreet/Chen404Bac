package com.chen404.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 配置类
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置 OpenAPI 基本信息
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chen404 博客系统 API")
                        .description("Chen404 个人博客后端接口文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Chen404")
                                .email("helychen@outlook.com")
                                .url("https://github.com/Chen404"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                // 配置 JWT Token 认证
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .components(new Components()
                        .addSecuritySchemes("Authorization",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 JWT Token，格式：Bearer {token}")));
    }

    /**
     * 公开 API 分组（不需要认证）
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("公开接口")
                .pathsToMatch(
                        "/auth/**",
                        "/articles/**",
                        "/categories/**",
                        "/tags/**",
                        "/archives/**",
                        "/comments/**",
                        "/friends/**",
                        "/site/**",
                        "/home/**"
                )
                .build();
    }

    /**
     * 管理 API 分组（需要认证）
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理接口")
                .pathsToMatch(
                        "/upload/**",
                        "/articles/admin/**"
                )
                .build();
    }

    /**
     * 全部 API 分组
     */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .pathsToMatch("/**")
                .build();
    }
}
