package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.DevelopmentHistoryVO;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.service.DevelopmentHistoryService;
import com.chen404.service.GitHubDevelopmentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 GitHub 开发同步配置接口。
 */
@Tag(name = "GitHub 开发同步配置", description = "后台维护开发历程的 GitHub 仓库与同步参数")
@RestController
@RequestMapping("/admin/development-history/config")
public class AdminGitHubDevelopmentConfigController {

    private final GitHubDevelopmentConfigService configService;
    private final DevelopmentHistoryService developmentHistoryService;

    public AdminGitHubDevelopmentConfigController(
            GitHubDevelopmentConfigService configService,
            DevelopmentHistoryService developmentHistoryService) {
        this.configService = configService;
        this.developmentHistoryService = developmentHistoryService;
    }

    @Operation(summary = "获取 GitHub 开发同步配置")
    @RequireAdmin
    @GetMapping
    public Result<GitHubDevelopmentAdminConfigDTO> getConfig() {
        return Result.success(configService.getAdminConfig());
    }

    @Operation(summary = "更新 GitHub 开发同步配置")
    @RequireAdmin
    @PutMapping
    public Result<GitHubDevelopmentAdminConfigDTO> updateConfig(
            @RequestBody GitHubDevelopmentAdminConfigDTO request) {
        return Result.success("保存成功", configService.updateAdminConfig(request));
    }

    @Operation(summary = "立即同步开发历程")
    @RequireAdmin
    @PostMapping("/refresh")
    public Result<DevelopmentHistoryVO> refresh() {
        return Result.success(developmentHistoryService.refreshDevelopmentHistory());
    }
}
