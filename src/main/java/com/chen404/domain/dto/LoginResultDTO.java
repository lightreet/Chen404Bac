package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Schema(description = "登录响应对象")
@Data
public class LoginResultDTO {

    /**
     * 访问Token
     */
    @Schema(description = "访问Token")
    private String token;

    /**
     * 刷新Token
     */
    @Schema(description = "刷新Token")
    private String refreshToken;

    /**
     * Token过期时间（秒）
     */
    @Schema(description = "Token过期时间，单位秒", example = "7200")
    private Integer expires;

    /**
     * 用户信息
     */
    @Schema(description = "当前登录用户资料")
    private UserProfileVO user;

    public static LoginResultDTO of(String token, String refreshToken, Integer expires, UserProfileVO user) {
        LoginResultDTO result = new LoginResultDTO();
        result.setToken(token);
        result.setRefreshToken(refreshToken);
        result.setExpires(expires);
        result.setUser(user);
        return result;
    }
}
