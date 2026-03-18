package com.chen404.domain.dto;

import lombok.Data;

/**
 * 刷新 Token 响应体
 */
@Data
public class TokenRefreshResultDTO {
    private String token;
    private String refreshToken;
    /**
     * token 过期秒数（与前端约定）
     */
    private Integer expires;

    public static TokenRefreshResultDTO of(String token, String refreshToken, Integer expires) {
        TokenRefreshResultDTO dto = new TokenRefreshResultDTO();
        dto.setToken(token);
        dto.setRefreshToken(refreshToken);
        dto.setExpires(expires);
        return dto;
    }
}

