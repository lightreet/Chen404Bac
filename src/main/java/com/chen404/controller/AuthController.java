package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.util.RequestAttrUtil;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RefreshTokenDTO;
import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.dto.SendCodeDTO;
import com.chen404.domain.dto.TokenRefreshResultDTO;
import com.chen404.domain.dto.UpdateProfileDTO;
import com.chen404.domain.entity.User;
import com.chen404.service.UserService;
import com.chen404.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器（登录、注册）
 */
@Tag(name = "认证管理", description = "用户登录、注册、验证码等相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private com.chen404.util.JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "支持用户名、邮箱、手机号三种登录方式")
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        try {
            LoginResultDTO result = userService.login(loginDTO);
            return Result.success("登录成功", result);
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "支持邮箱注册，需要验证码")
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDTO registerDTO) {
        try {
            // 验证邮箱验证码
            if (registerDTO.getEmail() != null && !registerDTO.getEmail().isEmpty()) {
                boolean valid = verificationCodeService.verifyCode(
                        registerDTO.getEmail(), "register", registerDTO.getCode());
                if (!valid) {
                    return Result.error(400, "验证码错误或已过期");
                }
            }

            User user = userService.register(registerDTO);
            return Result.success("注册成功", user);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 发送验证码
     */
    @Operation(summary = "发送验证码", description = "支持邮箱和手机号发送验证码")
    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendCode(@Valid @RequestBody SendCodeDTO sendCodeDTO) {
        try {
            String target = null;

            // 判断是邮箱还是手机号
            if (sendCodeDTO.getEmail() != null && !sendCodeDTO.getEmail().isEmpty()) {
                target = sendCodeDTO.getEmail();

                // 注册时检查邮箱是否已存在
                if ("register".equals(sendCodeDTO.getType()) && userService.isEmailExists(target)) {
                    return Result.error(400, "该邮箱已被注册");
                }
            } else if (sendCodeDTO.getPhone() != null && !sendCodeDTO.getPhone().isEmpty()) {
                target = sendCodeDTO.getPhone();

                // 注册时检查手机号是否已存在
                if ("register".equals(sendCodeDTO.getType()) && userService.isPhoneExists(target)) {
                    return Result.error(400, "该手机号已被注册");
                }
            } else {
                return Result.error(400, "请输入邮箱或手机号");
            }

            // 生成并发送验证码
            verificationCodeService.generateAndSendCode(target, sendCodeDTO.getType());

            Map<String, Object> result = new HashMap<>();
            result.put("expireSeconds", 300); // 5分钟有效期

            return Result.success("验证码发送成功", result);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息（支持 GET 与 POST，前端个人中心使用 GET）
     */
    @Operation(summary = "获取当前用户信息", description = "需要登录，从JWT Token中解析用户信息")
    @RequestMapping(value = "/info", method = { RequestMethod.GET, RequestMethod.POST })
    public Result<User> getUserInfo(HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        User user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 刷新访问 Token
     */
    @Operation(summary = "刷新Token", description = "使用 refreshToken 换取新的访问 token（无需登录）")
    @PostMapping("/refresh")
    public Result<TokenRefreshResultDTO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        try {
            String refreshToken = dto.getRefreshToken();
            var decoded = jwtUtil.verifyToken(refreshToken);
            String type = decoded.getClaim("type").asString();
            if (!"refresh".equals(type)) {
                return Result.error(401, "refreshToken无效");
            }
            Long userId = Long.valueOf(decoded.getSubject());
            String username = decoded.getClaim("username").asString();

            User user = userService.getById(userId);
            if (user == null || user.getStatus() == 0) {
                return Result.error(401, "用户不存在或已被禁用");
            }

            String newToken = jwtUtil.generateToken(userId, username);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            int expiresSeconds = expiration == null ? 0 : (int) (expiration / 1000);
            return Result.success(TokenRefreshResultDTO.of(newToken, newRefreshToken, expiresSeconds));
        } catch (Exception e) {
            return Result.error(401, "refreshToken无效或已过期");
        }
    }

    /**
     * 更新个人资料（昵称、头像）
     */
    @Operation(summary = "更新个人资料", description = "需要登录，可更新昵称与头像")
    @PutMapping("/profile")
    public Result<User> updateProfile(@Valid @RequestBody UpdateProfileDTO dto, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        try {
            User user = userService.updateProfile(userId, dto);
            return Result.success("更新成功", user);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 修改密码（并发送提醒邮件）
     */
    @Operation(summary = "修改密码", description = "需要登录，校验旧密码，成功后发送提醒邮件")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        try {
            String clientIp = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            userService.changePassword(userId, dto, clientIp, userAgent);
            return Result.success("修改成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 检查用户名是否存在
     */
    @Operation(summary = "检查用户名是否存在")
    @Parameter(name = "username", description = "用户名", required = true)
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = userService.isUsernameExists(username);
        return Result.success(exists);
    }

    /**
     * 检查邮箱是否存在
     */
    @Operation(summary = "检查邮箱是否存在")
    @Parameter(name = "email", description = "邮箱地址", required = true)
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        return Result.success(exists);
    }

    /**
     * 检查手机号是否存在
     */
    @Operation(summary = "检查手机号是否存在")
    @Parameter(name = "phone", description = "手机号", required = true)
    @GetMapping("/check-phone")
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        boolean exists = userService.isPhoneExists(phone);
        return Result.success(exists);
    }

    /**
     * 退出登录
     */
    @Operation(summary = "退出登录", description = "JWT无状态，客户端清除token即可")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT无状态，客户端清除token即可
        // 如需服务端控制，可将token加入黑名单（Redis）
        return Result.success("退出成功");
    }
}