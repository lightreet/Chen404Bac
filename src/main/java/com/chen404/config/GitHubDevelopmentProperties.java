package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHub 开发历程同步配置。
 * <p>
 * Token 仅在服务端使用；未配置时，服务会回退到公开 Atom 提交源。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.github-development")
public class GitHubDevelopmentProperties {

    private String owner = "lightreet";

    private List<String> repositories = new ArrayList<>(List.of("Chen404Fro", "Chen404Bac"));

    private String branch = "main";

    private String token = "";

    private int cacheMinutes = 30;

    private int apiCommitLimit = 100;

    private int requestTimeoutSeconds = 12;

    private String apiBaseUrl = "https://api.github.com";

    private String webBaseUrl = "https://github.com";
}
