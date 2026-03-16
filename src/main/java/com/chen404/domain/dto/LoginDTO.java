package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Schema(description = "登录请求参数")
@Data
public class LoginDTO {

    /**
     * 用户名/邮箱/手机号
     */
    @Schema(description = "用户名/邮箱/手机号", required = true, example = "helychen")
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（MD5加密后）
     */
    @Schema(description = "密码", required = true, example = "1312827920")
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码（可选）
     */
    @Schema(description = "验证码（可选）")
    private String captcha;
}
