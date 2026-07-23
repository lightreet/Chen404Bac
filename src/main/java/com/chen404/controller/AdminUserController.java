package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.UserConverter;
import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import com.chen404.domain.dto.UpdateTrustLevelDTO;
import com.chen404.domain.dto.UserProfileVO;
import com.chen404.domain.entity.User;
import com.chen404.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户控制器。
 */
@Tag(name = "管理员-用户", description = "管理员维护用户信任级别等信息")
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final UserConverter userConverter;

    public AdminUserController(UserService userService, UserConverter userConverter) {
        this.userService = userService;
        this.userConverter = userConverter;
    }

    @RequireAdmin
    @Operation(summary = "更新用户信任级别", description = "仅管理员可设置读者/知友状态")
    @Parameter(name = "id", description = "用户 ID", required = true)
    @PutMapping("/{id}/trust-level")
    public Result<UserProfileVO> updateTrustLevel(@PathVariable Long id, @RequestBody UpdateTrustLevelDTO dto) {
        if (dto == null || dto.getTrustLevel() == null) {
            return Result.error(ApiErrorCode.BAD_REQUEST, "trustLevel 不能为空");
        }
        try {
            User updated = userService.updateTrustLevel(id, dto.getTrustLevel());
            return Result.success("更新成功", userConverter.toVO(updated));
        } catch (RuntimeException e) {
            return Result.error(ApiErrorCode.BAD_REQUEST, e.getMessage());
        }
    }
}
