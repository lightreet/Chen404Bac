package com.chen404.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 提供一个空的 UserDetailsService
     * 阻止 Spring Security 自动生成默认密码
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（前后端分离项目）
            .csrf(AbstractHttpConfigurer::disable)
            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // Swagger/OpenAPI 文档接口
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                // 允许匿名访问的接口（明确列出根路径和子路径）
                .requestMatchers("/auth/**", "/home/**", "/site/**",
                        "/articles", "/articles/**",
                        "/categories", "/categories/**",
                        "/tags", "/tags/**",
                        "/archives", "/archives/**",
                        "/comments/**").permitAll()
                // 静态资源
                .requestMatchers("/", "/uploads/**").permitAll()
                // 以下接口在 Controller/拦截器层做权限控制，Spring Security 放行
                // 含 /api 前缀：部分环境下 Security 收到的 path 带 context-path
                .requestMatchers("/upload/**", "/admin/**", "/api/admin/**").permitAll()
                // 其他接口需要认证（兜底）
                .anyRequest().authenticated()
            )
            // 禁用session（使用JWT）
            .sessionManagement(AbstractHttpConfigurer::disable)
            // 禁用表单登录
            .formLogin(AbstractHttpConfigurer::disable)
            // 禁用HTTP Basic认证
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
