package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.FeatureToggleConfigDTO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.FeatureToggleService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台运行时功能开关接口。
 */
@Tag(name = "功能开关", description = "无需重启即可调整的业务功能开关")
@RestController
@RequestMapping("/admin/feature-toggles")
public class AdminFeatureToggleController {

    private final FeatureToggleService featureToggleService;

    public AdminFeatureToggleController(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    @RequireAdmin
    @Operation(summary = "获取运行时功能开关")
    @GetMapping
    public Result<FeatureToggleConfigDTO> getConfig() {
        return Result.success(featureToggleService.getAdminConfig());
    }

    @RequireAdmin
    @Operation(summary = "更新运行时功能开关")
    @PutMapping
    public Result<FeatureToggleConfigDTO> updateConfig(
            @RequestBody FeatureToggleConfigDTO request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long operatorId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("功能开关已保存", featureToggleService.updateAdminConfig(request, operatorId));
    }
}
