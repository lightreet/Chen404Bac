package com.chen404.domain.dto;

import com.chen404.domain.entity.User;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
public class LoginResultDTO {

    /**
     * 访问Token
     */
    private String token;

    /**
     * 刷新Token
     */
    private String refreshToken;

    /**
     * Token过期时间（秒）
     */
    private Integer expires;

    /**
     * 用户信息
     */
    private User user;

    public static LoginResultDTO of(String token, String refreshToken, Integer expires, User user) {
        LoginResultDTO result = new LoginResultDTO();
        result.setToken(token);
        result.setRefreshToken(refreshToken);
        result.setExpires(expires);
        result.setUser(user);
        return result;
    }
}
