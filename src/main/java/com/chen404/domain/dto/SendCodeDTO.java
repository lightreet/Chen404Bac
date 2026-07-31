package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 兼容旧客户端的手机号字段；短信通道接入前不会发送验证码。
     */
    @Schema(description = "手机号（预留字段，当前短信通道未开放）", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 验证码类型编码（register/login/reset）
     */
    @Schema(description = "验证码类型编码（register/login/reset）", required = true, example = "register")
    @NotBlank(message = "验证码类型不能为空")
    private String type;
}
