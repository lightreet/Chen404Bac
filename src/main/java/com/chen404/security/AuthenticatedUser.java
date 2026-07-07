package com.chen404.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spring Security 中保存的当前登录用户身份。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {

    private Long userId;

    private String username;

    private String roleCode;
}
