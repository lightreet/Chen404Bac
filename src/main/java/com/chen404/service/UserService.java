package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.entity.User;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录
     */
    LoginResultDTO login(LoginDTO loginDTO, String clientIp);

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

    User getPublicUser(Long userId);

    List<User> listPublicUsers();

    /**
     * 更新个人资料（昵称、头像）
     */
    User updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 更新用户信任级别（仅管理员调用）
     */
    User updateTrustLevel(Long userId, Integer trustLevel);

    /**
     * 修改密码（校验旧密码）
     */
    void changePassword(Long userId, ChangePasswordDTO dto, String clientIp, String userAgent);
}
