package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.entity.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录
     */
    LoginResultDTO login(LoginDTO loginDTO);

    /**
     * 用户注册
     */
    User register(RegisterDTO registerDTO);

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 检查用户名是否存在
     */
    boolean isUsernameExists(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean isEmailExists(String email);

    /**
     * 检查手机号是否存在
     */
    boolean isPhoneExists(String phone);

    /**
     * 获取当前登录用户
     */
    User getCurrentUser(Long userId);
}
