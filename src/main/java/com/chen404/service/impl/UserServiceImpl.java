package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.entity.Role;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserRole;
import com.chen404.mapper.RoleMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.mapper.UserRoleMapper;
import com.chen404.service.EmailService;
import com.chen404.service.UserService;
import com.chen404.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Override
    public LoginResultDTO login(LoginDTO loginDTO) {
        String account = loginDTO.getUsername();
        User user = null;

        // 判断登录方式：用户名、邮箱或手机号
        if (account.contains("@")) {
            user = userMapper.selectByEmail(account);
        } else if (account.matches("^1[3-9]\\d{9}$")) {
            user = userMapper.selectByPhone(account);
        } else {
            user = userMapper.selectByUsername(account);
        }

        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 验证密码（前端明文 + 后端BCrypt）
        boolean passwordValid = passwordEncoder.matches(loginDTO.getPassword(), user.getPassword());
        if (!passwordValid) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 更新登录信息
        user.setLastLoginTime(LocalDateTime.now());
        // 更新IP（从request获取，这里简化处理）
        user.setLastLoginIp("0.0.0.0");
        userMapper.updateById(user);

        // 获取用户角色
        List<Role> roles = roleMapper.selectRolesByUserId(user.getId());
        if (!roles.isEmpty()) {
            user.setRole(roles.get(0).getId().intValue());
        }

        // 清除敏感信息
        user.setPassword(null);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResultDTO.of(token, refreshToken, (int) (expiration / 1000), user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterDTO registerDTO) {
        // 检查用户名是否存在
        if (isUsernameExists(registerDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否存在
        if (StringUtils.hasText(registerDTO.getEmail()) && isEmailExists(registerDTO.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 检查手机号是否存在
        if (StringUtils.hasText(registerDTO.getPhone()) && isPhoneExists(registerDTO.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        // 密码加密（BCrypt）
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(StringUtils.hasText(registerDTO.getNickname()) ?
                registerDTO.getNickname() : registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setAvatar("/default-avatar.jpg");
        user.setStatus(1);

        // 设置验证状态（邮箱已验证）
        if (StringUtils.hasText(registerDTO.getEmail())) {
            user.setEmailVerified(1);
        }
        if (StringUtils.hasText(registerDTO.getPhone())) {
            user.setPhoneVerified(0); // 手机号暂未验证
        }

        userMapper.insert(user);

        // 分配默认角色（普通用户）
        Role userRole = roleMapper.selectByRoleCode("user");
        if (userRole != null) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(userRole.getId());
            userRoleMapper.insert(ur);
        }

        // 清除敏感信息
        user.setPassword(null);

        return user;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public boolean isUsernameExists(String username) {
        return userMapper.selectByUsername(username) != null;
    }

    @Override
    public boolean isEmailExists(String email) {
        return userMapper.selectByEmail(email) != null;
    }

    @Override
    public boolean isPhoneExists(String phone) {
        return userMapper.selectByPhone(phone) != null;
    }

    @Override
    public User getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 清除敏感信息
            user.setPassword(null);
            // 获取角色
            List<Role> roles = roleMapper.selectRolesByUserId(userId);
            if (!roles.isEmpty()) {
                user.setRole(roles.get(0).getId().intValue());
            }
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);
        return getCurrentUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto, String clientIp, String userAgent) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("当前密码错误");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);

        // 修改密码后发送提醒邮件（尽力而为，不影响主流程）
        if (StringUtils.hasText(user.getEmail())) {
            try {
                String subject = "Chen404 博客：密码已修改提醒";
                String content = "你的账号密码刚刚被修改。\n\n"
                        + "时间：" + LocalDateTime.now() + "\n"
                        + "IP：" + (clientIp == null ? "-" : clientIp) + "\n"
                        + "设备：" + (userAgent == null ? "-" : userAgent) + "\n\n"
                        + "若这不是你本人操作，请尽快登录并修改密码，或联系管理员。";
                emailService.sendEmail(user.getEmail(), subject, content);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
