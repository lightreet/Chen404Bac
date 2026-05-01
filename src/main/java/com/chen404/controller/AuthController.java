package com.chen404.controller;

import com.chen404.converter.UserConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ChangePasswordDTO;
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
import com.chen404.exception.TooManyRequestsException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.UserService;
import com.chen404.service.VerificationCodeService;
import com.chen404.util.RedisKeys;
import com.chen404.util.CurrentUserUtil;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
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
import org.springframework.web.bind.annotation.RequestMethod;
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

    public AuthController(
            UserService userService,
            VerificationCodeService verificationCodeService,
            com.chen404.util.JwtUtil jwtUtil,
            RedisUtil redisUtil,
            UserConverter userConverter) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.userConverter = userConverter;
    }

    @Value("${jwt.expiration}")
    private Long expiration;

    @Operation(summary = "用户登录", description = "支持用户名、邮箱、手机号三种登录方式")
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        try {
            LoginResultDTO result = userService.login(loginDTO, getClientIp(request));
            return Result.success("登录成功", result);
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    @Operation(summary = "用户注册", description = "支持邮箱注册，需要验证码")
    @PostMapping("/register")
    public Result<UserProfileVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        try {
            if (registerDTO.getEmail() != null && !registerDTO.getEmail().isEmpty()) {
                boolean valid = verificationCodeService.verifyCode(registerDTO.getEmail(), "register", registerDTO.getCode());
                if (!valid) {
                    return Result.error(400, "验证码错误或已过期");
                }
            }

            User user = userService.register(registerDTO);
            return Result.success("注册成功", userConverter.toVO(user));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "发送验证码", description = "支持邮箱和手机号发送验证码")
    @PostMapping("/send-code")
    public Result<SendCodeResultDTO> sendCode(@Valid @RequestBody SendCodeDTO sendCodeDTO) {
        try {
            String target;
            if (sendCodeDTO.getEmail() != null && !sendCodeDTO.getEmail().isEmpty()) {
                target = sendCodeDTO.getEmail();
                if ("register".equals(sendCodeDTO.getType()) && userService.isEmailExists(target)) {
                    return Result.error(400, "该邮箱已被注册");
                }
            } else if (sendCodeDTO.getPhone() != null && !sendCodeDTO.getPhone().isEmpty()) {
                target = sendCodeDTO.getPhone();
                if ("register".equals(sendCodeDTO.getType()) && userService.isPhoneExists(target)) {
                    return Result.error(400, "该手机号已被注册");
                }
            } else {
                return Result.error(400, "请输入邮箱或手机号");
            }

            verificationCodeService.generateAndSendCode(target, sendCodeDTO.getType());
            return Result.success("验证码发送成功", new SendCodeResultDTO(300));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "获取当前用户信息", description = "需要登录，从 JWT Token 中解析用户信息")
    @RequestMapping(value = "/info", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<UserProfileVO> getUserInfo(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        User user = userService.getCurrentUser(userId);
        return Result.success(userConverter.toVO(user));
    }

    @Operation(summary = "刷新 Token", description = "使用 refreshToken 换取新的访问 token，无需重新登录")
    @PostMapping("/refresh")
    public Result<TokenRefreshResultDTO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        try {
            String refreshToken = dto.getRefreshToken();
            var decoded = jwtUtil.verifyToken(refreshToken);
            String type = decoded.getClaim("type").asString();
            if (!"refresh".equals(type)) {
                return Result.error(401, "refreshToken 无效");
            }
            String tokenId = decoded.getId();
            if (tokenId == null || redisUtil.exists(RedisKeys.refreshTokenBlacklist(tokenId))) {
                return Result.error(401, "refreshToken 已失效");
            }
            Long userId = Long.valueOf(decoded.getSubject());
            String username = decoded.getClaim("username").asString();

            User user = userService.getById(userId);
            if (user == null || user.getStatus() == 0) {
                return Result.error(401, "用户不存在或已被禁用");
            }

            revokeRefreshToken(decoded);
            String newToken = jwtUtil.generateToken(userId, username);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            int expiresSeconds = expiration == null ? 0 : (int) (expiration / 1000);
            return Result.success(TokenRefreshResultDTO.of(newToken, newRefreshToken, expiresSeconds));
        } catch (Exception e) {
            return Result.error(401, "refreshToken 无效或已过期");
        }
    }

    @Operation(summary = "更新个人资料", description = "需要登录，可更新昵称与头像")
    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(
            @Valid @RequestBody UpdateProfileDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        try {
            User user = userService.updateProfile(userId, dto);
            return Result.success("更新成功", userConverter.toVO(user));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "修改密码", description = "需要登录，校验旧密码，成功后发送提醒邮件")
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        try {
            String clientIp = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            userService.changePassword(userId, dto, clientIp, userAgent);
            return Result.success("修改成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "检查用户名是否存在")
    @Parameter(name = "username", description = "用户名", required = true)
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = userService.isUsernameExists(username);
        return Result.success(exists);
    }

    @Operation(summary = "检查邮箱是否存在")
    @Parameter(name = "email", description = "邮箱地址", required = true)
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        return Result.success(exists);
    }

    @Operation(summary = "检查手机号是否存在")
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
                var decoded = jwtUtil.verifyToken(dto.getRefreshToken());
                if ("refresh".equals(decoded.getClaim("type").asString())) {
                    revokeRefreshToken(decoded);
                }
            } catch (Exception ignored) {
            }
        }
        return Result.success("退出成功");
    }

    private void revokeRefreshToken(com.auth0.jwt.interfaces.DecodedJWT decoded) {
        if (decoded == null || decoded.getId() == null) {
            return;
        }
        long ttlMillis = jwtUtil.getRemainingMillis(decoded);
        if (ttlMillis <= 0) {
            return;
        }
        redisUtil.setString(RedisKeys.refreshTokenBlacklist(decoded.getId()), "1", Duration.ofMillis(ttlMillis));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            ip = commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        } else {
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                ip = ip.trim();
            } else {
                ip = request.getRemoteAddr();
            }
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
