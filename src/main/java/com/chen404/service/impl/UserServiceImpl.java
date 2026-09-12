package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.converter.UserConverter;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.ForgotPasswordDTO;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.entity.Role;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserRole;
import com.chen404.domain.enums.UserStatusEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.enums.UserTrustLevelEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ConflictException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.mapper.RoleMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.mapper.UserRoleMapper;
import com.chen404.service.AuthSessionService;
import com.chen404.service.EmailService;
import com.chen404.service.FileClaim;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.chen404.service.UserService;
import com.chen404.service.support.UserAccessProfileSupport;
import com.chen404.util.JwtUtil;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_MEMBER_AVATAR = "/default-member-avatar.svg";
    private static final int EMAIL_VERIFIED = 1;
    private static final int LOGIN_FAIL_LIMIT = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_BLOCK_TTL = Duration.ofMinutes(15);
    private static final String PASSWORD_CHANGE_SUBJECT = "Chen404 账号密码已修改提醒";

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

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserConverter userConverter;

    @Autowired
    private FileReferenceService fileReferenceService;

    @Autowired
    private AuthSessionService authSessionService;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Override
    public User findAccount(Long userId) {
        return userId == null ? null : getById(userId);
    }

    @Override
    public LoginResultDTO login(LoginDTO loginDTO, String clientIp) {
        String account = loginDTO.getUsername();
        assertLoginAllowed(account, clientIp);
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
            recordLoginFailure(account, clientIp);
            throw new UnauthorizedException("用户不存在或密码错误");
        }

        // 检查账号是否禁用
        if (!UserStatusEnum.isEnabled(user.getStatus())) {
            throw new UnauthorizedException("账号已被禁用");
        }

        // 校验密码（BCrypt）
        boolean passwordValid = passwordEncoder.matches(loginDTO.getPassword(), user.getPassword());
        if (!passwordValid) {
            recordLoginFailure(account, clientIp);
            throw new UnauthorizedException("用户不存在或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(normalizeClientIp(clientIp));
        if (userMapper.updateLoginAudit(user.getId(), user.getPassword(), user.getLastLoginTime(), user.getLastLoginIp()) != 1) {
            throw new UnauthorizedException("账号状态已变化，请重新登录");
        }
        clearLoginFailureState(account, clientIp);

        userAccessProfileSupport.enrichUserProfile(user);

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResultDTO.of(token, refreshToken, (int) (expiration / 1000), userConverter.toVO(user));
    }

    private void assertLoginAllowed(String account, String clientIp) {
        if (redisUtil.exists(RedisKeys.loginBlock(loginScope("account", account)))
                || redisUtil.exists(RedisKeys.loginBlock(loginScope("ip", normalizeClientIp(clientIp))))) {
            throw new TooManyRequestsException("登录失败次数过多，请 15 分钟后再试");
        }
    }

    private void recordLoginFailure(String account, String clientIp) {
        raiseLoginFailure(loginScope("account", account));
        raiseLoginFailure(loginScope("ip", normalizeClientIp(clientIp)));
    }

    private void raiseLoginFailure(String scope) {
        String countKey = RedisKeys.loginFailCount(scope);
        Long count = redisUtil.increment(countKey);
        if (count != null && count == 1L) {
            redisUtil.expire(countKey, LOGIN_FAIL_WINDOW);
        }
        if (count != null && count >= LOGIN_FAIL_LIMIT) {
            redisUtil.setString(RedisKeys.loginBlock(scope), "1", LOGIN_BLOCK_TTL);
        }
    }

    private void clearLoginFailureState(String account, String clientIp) {
        String accountScope = loginScope("account", account);
        String ipScope = loginScope("ip", normalizeClientIp(clientIp));
        redisUtil.delete(RedisKeys.loginFailCount(accountScope));
        redisUtil.delete(RedisKeys.loginBlock(accountScope));
        redisUtil.delete(RedisKeys.loginFailCount(ipScope));
        redisUtil.delete(RedisKeys.loginBlock(ipScope));
    }

    private static String loginScope(String type, String value) {
        return type + ":" + (StringUtils.hasText(value) ? value.trim() : "anonymous");
    }

    private static String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim().replace(':', '_');
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterDTO registerDTO) {
        if (registerDTO == null || !registerDTO.isSupportedRegistrationChannel()) {
            throw new BadRequestException("当前仅支持邮箱注册");
        }
        String email = registerDTO.getEmail().trim();
        // 邮箱唯一
        if (isEmailExists(email) || isUsernameExists(email)) {
            throw new ConflictException("邮箱已被注册");
        }

        Role defaultRole = roleMapper.selectByRoleCode(UserRoleEnum.USER.getRoleCode());
        if (defaultRole == null) {
            log.error("[USER_REGISTER_ROLE_MISSING] roleCode={}", UserRoleEnum.USER.getRoleCode());
            throw new IllegalStateException("注册默认角色未配置");
        }

        // 构造用户实体
        User user = new User();
        // 用户名直接使用已验证邮箱；用户 ID 仍沿用原有分配方式。
        user.setUsername(email);
        // 密码 BCrypt 加密
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setEmail(email);
        user.setAvatar(DEFAULT_MEMBER_AVATAR);
        user.setStatus(UserStatusEnum.ENABLED.getValue());
        user.setTrustLevel(UserTrustLevelEnum.NORMAL.getLevel());

        // 控制器完成邮箱验证码核验后才进入注册事务。
        user.setEmailVerified(EMAIL_VERIFIED);
        user.setNickname(StringUtils.hasText(registerDTO.getNickname()) ?
                registerDTO.getNickname().trim() : user.getUsername());
        try {
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("用户创建失败");
            }
        } catch (DuplicateKeyException conflict) {
            // 预检查不能避免并发注册，唯一索引冲突统一返回业务冲突，不暴露数据库信息。
            throw new ConflictException("邮箱已被注册");
        }

        UserRole membership = new UserRole();
        membership.setUserId(user.getId());
        membership.setRoleId(defaultRole.getId());
        if (userRoleMapper.insert(membership) != 1) {
            log.error("[USER_REGISTER_ROLE_BIND_FAILED] userId={} roleId={}", user.getId(), defaultRole.getId());
            throw new IllegalStateException("注册角色绑定失败");
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
    public User getPublicUser(Long userId) {
        User user = lambdaQuery()
                .eq(User::getId, userId)
                .eq(User::getStatus, UserStatusEnum.ENABLED.getValue())
                .eq(User::getProfileVisibility, 1)
                .one();
        if (user == null) {
            return null;
        }
        return userAccessProfileSupport.enrichUserProfile(user);
    }

    @Override
    public List<User> listPublicUsers() {
        return lambdaQuery()
                .eq(User::getStatus, UserStatusEnum.ENABLED.getValue())
                .eq(User::getProfileVisibility, 1)
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
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        String oldAvatar = user.getAvatar();
        String newAvatar = dto.getAvatar();

        user.setNickname(dto.getNickname());
        user.setAvatar(newAvatar);
        user.setBio(StringUtils.hasText(dto.getBio()) ? dto.getBio().trim() : null);
        if (dto.getProfileVisible() != null) {
            user.setProfileVisibility(Boolean.TRUE.equals(dto.getProfileVisible()) ? 1 : 0);
        }
        if (dto.getEmailPublic() != null) {
            user.setEmailPublic(Boolean.TRUE.equals(dto.getEmailPublic()) ? 1 : 0);
        }
        if (!Integer.valueOf(1).equals(user.getProfileVisibility())) {
            user.setEmailPublic(0);
        }

        if (!Objects.equals(oldAvatar, newAvatar)) {
            if (StringUtils.hasText(newAvatar)) {
                sysFileService.claimPermanentFiles(
                        userId,
                        List.of(FileClaim.byUrl(newAvatar)),
                        SysFile.RefType.AVATAR,
                        userId
                );
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
                sysFileService.requestDeletionByUrl(oldAvatar, userId);
            }
        }

        if (userMapper.updateProfileFields(user) != 1) {
            throw new ResourceNotFoundException("用户不存在");
        }
        fileReferenceService.syncUserAvatarReference(userId, user.getAvatar());
        return getCurrentUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateTrustLevel(Long userId, Integer trustLevel) {
        if (!UserTrustLevelEnum.isValidLevel(trustLevel)) {
            throw new BadRequestException("信任级别无效，仅允许读者(0)或知友(1)");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        if (userMapper.updateTrustLevel(userId, trustLevel) != 1) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return getCurrentUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto, String clientIp, String userAgent) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("当前密码错误");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("新密码不能与当前密码相同");
        }
        persistPasswordChange(user, dto.getNewPassword());
        authSessionService.revokeAll(userId);
        log.info("[USER_PASSWORD_CHANGED] userId={} via=auth-change clientIpPresent={}",
                userId, StringUtils.hasText(clientIp));

        sendPasswordChangeNotification(user, clientIp, userAgent, "账号设置");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordByEmail(ForgotPasswordDTO dto, String clientIp, String userAgent) {
        User user = userMapper.selectByEmail(dto.getEmail());
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (!UserStatusEnum.isEnabled(user.getStatus())) {
            throw new UnauthorizedException("账号已被禁用");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("新密码不能与当前密码相同");
        }

        persistPasswordChange(user, dto.getNewPassword());
        authSessionService.revokeAll(user.getId());
        log.info("[USER_PASSWORD_RESET] userId={} clientIpPresent={}",
                user.getId(), StringUtils.hasText(clientIp));

        sendPasswordChangeNotification(user, clientIp, userAgent, "邮箱找回");
    }

    /** 仅更新凭据，旧密码已变更或账号被禁用时回报冲突。 */
    private void persistPasswordChange(User user, String newPassword) {
        String encoded = passwordEncoder.encode(newPassword);
        if (userMapper.updatePasswordIfUnchanged(user.getId(), user.getPassword(), encoded) != 1) {
            throw new ConflictException("账号状态或密码已变化，请重新验证后重试");
        }
    }

    /** 若已绑定邮箱则发送密码修改提醒。 */
    private void sendPasswordChangeNotification(User user, String clientIp, String userAgent, String scene) {
        if (StringUtils.hasText(user.getEmail())) {
            try {
                String content = "您好，您的账号密码已通过" + scene + "完成修改：\n"
                        + "时间：" + LocalDateTime.now() + "\n"
                        + "IP：" + (clientIp == null ? "-" : clientIp) + "\n"
                        + "设备：" + (userAgent == null ? "-" : userAgent) + "\n\n"
                        + "如非本人操作，请尽快登录并修改密码。";
                emailService.sendEmail(user.getEmail(), PASSWORD_CHANGE_SUBJECT, content);
            } catch (Exception ex) {
                log.error("[USER_PASSWORD_NOTIFY_FAIL] userId={}", user.getId(), ex);
            }
        }
    }

}
