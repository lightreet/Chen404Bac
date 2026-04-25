package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.entity.Role;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserRole;
import com.chen404.mapper.RoleMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.mapper.UserRoleMapper;
import com.chen404.service.EmailService;
import com.chen404.service.SysFileService;
import com.chen404.service.UserService;
import com.chen404.service.support.UserAccessProfileSupport;
import com.chen404.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_MEMBER_AVATAR = "/default-member-avatar.svg";

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

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Override
    public LoginResultDTO login(LoginDTO loginDTO) {
        String account = loginDTO.getUsername();
        User user = null;

        // 支持邮箱、手机号或用户名登录
        if (account.contains("@")) {
            user = userMapper.selectByEmail(account);
        } else if (account.matches("^1[3-9]\\d{9}$")) {
            user = userMapper.selectByPhone(account);
        } else {
            user = userMapper.selectByUsername(account);
        }

        if (user == null) {
            throw new RuntimeException("用户不存在或密码错误");
        }

        // 检查账号是否禁用
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 校验密码（BCrypt）
        boolean passwordValid = passwordEncoder.matches(loginDTO.getPassword(), user.getPassword());
        if (!passwordValid) {
            throw new RuntimeException("用户不存在或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        // 最后登录 IP：暂无请求上下文时使用占位（可从 request 注入真实 IP）
        user.setLastLoginIp("0.0.0.0");
        userMapper.updateById(user);

        userAccessProfileSupport.enrichUserProfile(user);

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResultDTO.of(token, refreshToken, (int) (expiration / 1000), user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterDTO registerDTO) {
        // 用户名唯一
        if (isUsernameExists(registerDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        // 邮箱唯一
        if (StringUtils.hasText(registerDTO.getEmail()) && isEmailExists(registerDTO.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 手机号唯一
        if (StringUtils.hasText(registerDTO.getPhone()) && isPhoneExists(registerDTO.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }

        // 构造用户实体
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        // 密码 BCrypt 加密
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(StringUtils.hasText(registerDTO.getNickname()) ?
                registerDTO.getNickname() : registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setAvatar(DEFAULT_MEMBER_AVATAR);
        user.setStatus(1);
        user.setTrustLevel(User.TrustLevel.NORMAL);

        // 填写邮箱则标记为已验证；手机号默认未验证（需后续验证流程）
        if (StringUtils.hasText(registerDTO.getEmail())) {
            user.setEmailVerified(1);
        }
        if (StringUtils.hasText(registerDTO.getPhone())) {
            user.setPhoneVerified(0);
        }

        userMapper.insert(user);

        // 绑定默认 user 角色
        Role userRole = roleMapper.selectByRoleCode("user");
        if (userRole != null) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(userRole.getId());
            userRoleMapper.insert(ur);
        }

        return getCurrentUser(user.getId());
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
        return userAccessProfileSupport.loadUserProfile(userId);
    }

    @Override
    public List<User> listPublicUsers() {
        return lambdaQuery()
                .eq(User::getStatus, 1)
                .orderByDesc(User::getTrustLevel)
                .orderByAsc(User::getCreateTime)
                .list()
                .stream()
                .map(userAccessProfileSupport::enrichUserProfile)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String oldAvatar = user.getAvatar();
        String newAvatar = dto.getAvatar();

        user.setNickname(dto.getNickname());
        user.setAvatar(newAvatar);
        user.setBio(StringUtils.hasText(dto.getBio()) ? dto.getBio().trim() : null);

        if (!Objects.equals(oldAvatar, newAvatar)) {
            if (StringUtils.hasText(newAvatar)) {
                sysFileService.convertToPermanent(List.of(newAvatar), SysFile.RefType.AVATAR, userId);
                SysFile f = sysFileService.findByFileUrl(newAvatar);
                if (f != null && Objects.equals(f.getUserId(), userId)
                        && SysFile.RefType.AVATAR.equals(f.getRefType())) {
                    user.setAvatarFileId(f.getId());
                } else {
                    user.setAvatarFileId(null);
                }
            } else {
                user.setAvatarFileId(null);
            }
            if (StringUtils.hasText(oldAvatar) && !oldAvatar.startsWith("/")) {
                sysFileService.deleteByUrl(oldAvatar, userId);
            }
        }

        userMapper.updateById(user);
        return getCurrentUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateTrustLevel(Long userId, Integer trustLevel) {
        if (!Objects.equals(trustLevel, User.TrustLevel.NORMAL)
                && !Objects.equals(trustLevel, User.TrustLevel.FRIEND)) {
            throw new RuntimeException("信任级别无效，仅允许读者(0)或知友(1)");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setTrustLevel(trustLevel);
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

        // 若已绑定邮箱则发送密码修改提醒
        if (StringUtils.hasText(user.getEmail())) {
            try {
                String subject = "Chen404 账号密码已修改提醒";
                String content = "您好，您的账号密码已在下列环境下完成修改：\n"
                        + "时间：" + LocalDateTime.now() + "\n"
                        + "IP：" + (clientIp == null ? "-" : clientIp) + "\n"
                        + "设备：" + (userAgent == null ? "-" : userAgent) + "\n\n"
                        + "如非本人操作，请尽快登录并修改密码。";
                emailService.sendEmail(user.getEmail(), subject, content);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

}
