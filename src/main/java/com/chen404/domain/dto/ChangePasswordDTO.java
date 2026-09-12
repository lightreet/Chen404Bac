package com.chen404.domain.dto;

import com.chen404.domain.UserConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO
 */
@Schema(description = "修改密码请求参数")
@Data
public class ChangePasswordDTO {

    @Schema(description = "当前密码", required = true)
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码（6-20位）", required = true)
    @NotBlank(message = "新密码不能为空")
    @Size(min = UserConstraints.PASSWORD_MIN_LENGTH, max = UserConstraints.PASSWORD_MAX_LENGTH, message = "新密码长度6-20位")
    private String newPassword;
}

