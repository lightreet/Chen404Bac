package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiConfigTestRequest;
import com.chen404.domain.dto.AiConfigTestResponse;
import com.chen404.service.AiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 AI 配置接口。
 */
@Tag(name = "AI 配置", description = "后台 AI 模型与 Lyra 女仆配置")
@RestController
@RequestMapping("/admin/ai/config")
public class AdminAiConfigController {

    private final AiConfigService aiConfigService;

    public AdminAiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @Operation(summary = "获取 AI 配置")
    @RequireAdmin
    @GetMapping
    public Result<AiAdminConfigDTO> getConfig() {
        return Result.success(aiConfigService.getAdminConfig());
    }

    @Operation(summary = "更新 AI 配置")
    @RequireAdmin
    @PutMapping
    public Result<AiAdminConfigDTO> updateConfig(@RequestBody AiAdminConfigDTO request) {
        return Result.success("保存成功", aiConfigService.updateAdminConfig(request));
    }

    @Operation(summary = "测试 AI 连接")
    @RequireAdmin
    @PostMapping("/test-connection")
    public Result<AiConfigTestResponse> testConnection(@RequestBody AiConfigTestRequest request) {
        return Result.success(aiConfigService.testConnection(request));
    }
}
