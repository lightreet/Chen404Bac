package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求DTO
 */
@Schema(description = "发送验证码请求参数")
@Data
public class SendCodeDTO {

    /**
     * 邮箱（邮箱验证时必填）
     */
    @Schema(description = "邮箱（邮箱验证时必填）", example = "test@example.com")
    private String email;

    /**
     * 手机号（短信验证时必填）
     */
    @Schema(description = "手机号（短信验证时必填）", example = "13800138000")
    private String phone;

    /**
     * 验证码类型：register-注册 login-登录 reset-重置密码
     */
    @Schema(description = "验证码类型：register-注册 login-登录 reset-重置密码", required = true, example = "register")
    @NotBlank(message = "验证码类型不能为空")
    private String type;
}
