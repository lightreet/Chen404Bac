package com.chen404.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求DTO
 */
@Schema(description = "注册请求参数")
@Data
public class RegisterDTO {

    /**
     * 用户名（3-20位字母数字下划线）
     */
    @Schema(description = "用户名，3-20位字母数字下划线", required = true, example = "testuser")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度3-20位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    /**
     * 密码（6-20位）
     */
    @Schema(description = "密码，6-20位", required = true, example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String password;

    /**
     * 昵称（可选）
     */
    @Schema(description = "昵称（可选）", example = "测试用户")
    private String nickname;

    /**
     * 邮箱（邮箱注册时必填）
     */
    @Schema(description = "邮箱（邮箱注册时必填）", example = "test@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 手机号（手机注册时必填）
     */
    @Schema(description = "手机号（手机注册时必填）", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 验证码
     */
    @Schema(description = "验证码", required = true, example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 注册类型；保留字段用于兼容旧客户端，当前只接受 email。
     */
    @Schema(description = "注册类型，当前只支持 email", example = "email")
    private String registerType;

    /**
     * 短信通道尚未接入时，只允许使用已验证的邮箱注册，避免保存未验证手机号。
     */
    @JsonIgnore
    @AssertTrue(message = "当前仅支持邮箱注册，请填写邮箱并选择 email 注册类型")
    public boolean isSupportedRegistrationChannel() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean emailType = registerType == null
                || registerType.isBlank()
                || "email".equalsIgnoreCase(registerType.trim());
        return hasEmail && !hasPhone && emailType;
    }
}
