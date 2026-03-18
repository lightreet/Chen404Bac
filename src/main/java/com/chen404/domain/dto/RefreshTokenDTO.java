package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求体
 */
@Data
public class RefreshTokenDTO {
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}

