package com.chen404.controller;

import com.chen404.converter.UserConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.ForgotPasswordDTO;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RefreshTokenDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.SendCodeDTO;
import com.chen404.domain.dto.SendCodeResultDTO;
import com.chen404.domain.dto.TokenRefreshResultDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.dto.UserProfileVO;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.VerificationCodeTypeEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AuthSessionService;
import com.chen404.service.UserService;
import com.chen404.service.VerificationCodeService;
import com.chen404.util.AuthConstants;
import com.chen404.util.CurrentUserUtil;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import com.chen404.util.WebRequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证控制器（登录、注册）。
 */
@Tag(name = "认证管理", description = "用户登录、注册、验证码等相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final com.chen404.util.JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final UserConverter userConverter;
    private final AuthSessionService authSessionService;

    public AuthController(
            UserService userService,
            VerificationCodeService verificationCodeService,
            com.chen404.util.JwtUtil jwtUtil,
            RedisUtil redisUtil,
            UserConverter userConverter,
            AuthSessionService authSessionService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.userConverter = userConverter;
        this.authSessionService = authSessionService;
    }

    @Value("${jwt.expiration}")
    private Long expiration;

    @Operation(summary = "用户登录", description = "支持用户名、邮箱、手机号三种登录方式")
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        LoginResultDTO result = userService.login(loginDTO, WebRequestUtil.getClientIp(request));
        return Result.success("登录成功", result);
    }

    @Operation(summary = "用户注册", description = "支持邮箱注册，需要验证码")
    @PostMapping("/register")
    public Result<UserProfileVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        boolean valid = verificationCodeService.verifyCode(
                registerDTO.getEmail(),
                VerificationCodeTypeEnum.REGISTER,
                registerDTO.getCode()
        );
        if (!valid) {
            throw new BadRequestException("验证码错误、已过期或错误次数过多");
        }

        User user = userService.register(registerDTO);
        return Result.success("注册成功", userConverter.toVO(user));
    }

    @Operation(summary = "发送邮箱验证码", description = "短信通道尚未接入，当前仅支持邮箱验证码")
    @PostMapping("/send-code")
    public Result<SendCodeResultDTO> sendCode(@Valid @RequestBody SendCodeDTO sendCodeDTO) {
        VerificationCodeTypeEnum codeType = VerificationCodeTypeEnum.fromCode(sendCodeDTO.getType());
        String target;
        if (sendCodeDTO.getEmail() != null && !sendCodeDTO.getEmail().isEmpty()) {
            target = sendCodeDTO.getEmail();
            if (VerificationCodeTypeEnum.REGISTER == codeType && userService.isEmailExists(target)) {
                throw new BadRequestException("该邮箱已被注册");
            }
            if (VerificationCodeTypeEnum.RESET == codeType && !userService.isEmailExists(target)) {
                throw new BadRequestException("该邮箱尚未注册");
            }
        } else if (sendCodeDTO.getPhone() != null && !sendCodeDTO.getPhone().isEmpty()) {
            throw new BadRequestException("手机号验证码暂未开放，请使用邮箱");
        } else {
            throw new BadRequestException("请输入邮箱");
        }

        verificationCodeService.generateAndSendCode(target, codeType);
        return Result.success("验证码发送成功", new SendCodeResultDTO(AuthConstants.SEND_CODE_EXPIRE_SECONDS));
    }

    @Operation(summary = "忘记密码", description = "通过邮箱验证码重置密码，无需登录")
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto, HttpServletRequest request) {
        if (!userService.isEmailExists(dto.getEmail())) {
            throw new BadRequestException("该邮箱尚未注册");
        }

        boolean valid = verificationCodeService.verifyCode(dto.getEmail(), VerificationCodeTypeEnum.RESET, dto.getCode());
        if (!valid) {
            throw new BadRequestException("验证码错误、已过期或错误次数过多");
        }

        String clientIp = WebRequestUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        userService.resetPasswordByEmail(dto, clientIp, userAgent);
        return Result.success("密码重置成功");
    }

    @Operation(summary = "获取当前用户信息", description = "需要登录，从 JWT Token 中解析当前用户资料")
    @GetMapping("/info")
    public Result<UserProfileVO> getUserInfo(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        User user = userService.getCurrentUser(userId);
        return Result.success(userConverter.toVO(user));
    }

    @Operation(hidden = true)
    @PostMapping("/info")
    public Result<UserProfileVO> getUserInfoLegacyPost(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return getUserInfo(currentUser);
    }

    @Operation(summary = "刷新 Token", description = "使用 refreshToken 换取新的访问 token，无需重新登录")
    @PostMapping("/refresh")
    public Result<TokenRefreshResultDTO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        try {
            String refreshToken = dto.getRefreshToken();
            var decoded = jwtUtil.verifyRefreshToken(refreshToken);
            String tokenId = decoded.getId();
            if (tokenId == null) {
                throw new UnauthorizedException("refreshToken 无效");
            }
            Long userId = jwtUtil.getUserId(decoded);
            String username = decoded.getClaim("username").asString();

            User user = userService.getById(userId);
            if (user == null || user.getStatus() == 0) {
                throw new UnauthorizedException("用户不存在或已被禁用");
            }
            if (!authSessionService.isCurrent(userId, decoded)) {
                throw new UnauthorizedException("登录状态已失效，请重新登录");
            }
            if (!revokeRefreshToken(decoded)) {
                throw new UnauthorizedException("refreshToken 已失效");
            }

            String newToken = jwtUtil.generateToken(userId, username);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            int expiresSeconds = expiration == null ? 0 : (int) (expiration / 1000);
            return Result.success(TokenRefreshResultDTO.of(newToken, newRefreshToken, expiresSeconds));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("refreshToken 无效或已过期");
        }
    }

    @Operation(summary = "更新个人资料", description = "需要登录，可更新昵称与头像")
    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(
            @Valid @RequestBody UpdateProfileDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        User user = userService.updateProfile(userId, dto);
        return Result.success("更新成功", userConverter.toVO(user));
    }

    @Operation(summary = "修改密码", description = "需要登录，校验旧密码，成功后发送提醒邮件")
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        String clientIp = WebRequestUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        userService.changePassword(userId, dto, clientIp, userAgent);
        return Result.success("修改成功");
    }

    @Operation(summary = "检查用户名是否存在", description = "用于注册时校验用户名是否已被占用")
    @Parameter(name = "username", description = "用户名", required = true)
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = userService.isUsernameExists(username);
        return Result.success(exists);
    }

    @Operation(summary = "检查邮箱是否存在", description = "用于注册时校验邮箱地址是否已被注册")
    @Parameter(name = "email", description = "邮箱地址", required = true)
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        return Result.success(exists);
    }

    @Operation(summary = "检查手机号是否存在", description = "用于注册时校验手机号是否已被注册")
    @Parameter(name = "phone", description = "手机号", required = true)
    @GetMapping("/check-phone")
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        boolean exists = userService.isPhoneExists(phone);
        return Result.success(exists);
    }

    @Operation(summary = "退出登录", description = "客户端清除 token，并吊销当前 refreshToken")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshTokenDTO dto) {
        if (dto != null && dto.getRefreshToken() != null && !dto.getRefreshToken().isBlank()) {
            try {
                var decoded = jwtUtil.verifyRefreshToken(dto.getRefreshToken());
                revokeRefreshToken(decoded);
            } catch (Exception ignored) {
            }
        }
        return Result.success("退出成功");
    }

    private boolean revokeRefreshToken(com.auth0.jwt.interfaces.DecodedJWT decoded) {
        if (decoded == null || decoded.getId() == null) {
            return false;
        }
        long ttlMillis = jwtUtil.getRemainingMillis(decoded);
        if (ttlMillis <= 0) {
            return false;
        }
        return redisUtil.setIfAbsent(
                RedisKeys.refreshTokenBlacklist(decoded.getId()),
                "1",
                Duration.ofMillis(ttlMillis)
        );
    }

}
