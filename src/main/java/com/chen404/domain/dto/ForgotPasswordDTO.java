package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码请求 DTO
 */
@Schema(description = "忘记密码请求参数")
@Data
public class ForgotPasswordDTO {

    @Schema(description = "已注册邮箱", required = true, example = "chen404@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "邮箱验证码", required = true, example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 6, message = "验证码长度需为 4-6 位")
    private String code;

    @Schema(description = "新密码（6-20位）", required = true, example = "newPassword123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度6-20位")
    private String newPassword;
}
