package com.chen404.service.impl;

import com.chen404.domain.enums.VerificationCodeTypeEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.service.EmailService;
import com.chen404.service.VerificationCodeService;
import com.chen404.util.AuthConstants;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final long CODE_EXPIRE_MINUTES = AuthConstants.SEND_CODE_EXPIRE_SECONDS / 60L;
    private static final long SEND_INTERVAL_SECONDS = AuthConstants.SEND_CODE_INTERVAL_SECONDS;
    private static final int MAX_DAILY_SEND_COUNT = AuthConstants.MAX_DAILY_SEND_CODE_COUNT;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int DECIMAL_RADIX = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisUtil redisUtil;
    private final EmailService emailService;

    public VerificationCodeServiceImpl(RedisUtil redisUtil, EmailService emailService) {
        this.redisUtil = redisUtil;
        this.emailService = emailService;
    }

    @Override
    public String generateAndSendCode(String target, VerificationCodeTypeEnum type) {
        String normalizedTarget = requireSupportedTarget(target);
        if (type == null) {
            throw new BadRequestException("验证码类型不能为空");
        }

        String intervalKey = RedisKeys.verifyInterval(type, normalizedTarget);
        boolean intervalReserved = redisUtil.setIfAbsent(
                intervalKey,
                AuthConstants.REDIS_FLAG_VALUE,
                Duration.ofSeconds(SEND_INTERVAL_SECONDS)
        );
        if (!intervalReserved) {
            throw new TooManyRequestsException("发送过于频繁，请稍后再试");
        }

        Long dailyCount = redisUtil.incrementWithInitialTtl(
                RedisKeys.verifyDailyCount(type, normalizedTarget),
                durationUntilEndOfDay()
        );
        if (dailyCount == null) {
            redisUtil.delete(intervalKey);
            throw new IllegalStateException("验证码发送计数失败");
        }
        if (dailyCount > MAX_DAILY_SEND_COUNT) {
            redisUtil.delete(intervalKey);
            throw new TooManyRequestsException("今日发送次数已达上限，请明天再试");
        }

        String code = generateCode();
        String codeKey = RedisKeys.verifyCode(type, normalizedTarget);
        redisUtil.setString(codeKey, code, Duration.ofMinutes(CODE_EXPIRE_MINUTES));

        try {
            emailService.sendVerificationCode(normalizedTarget, code, type);
        } catch (RuntimeException ex) {
            redisUtil.delete(codeKey);
            redisUtil.delete(intervalKey);
            throw ex;
        }

        log.info("[VERIFICATION_CODE_SENT] channel=email type={}", type.getCode());
        return code;
    }

    @Override
    public boolean verifyCode(String target, VerificationCodeTypeEnum type, String code) {
        String normalizedTarget = requireSupportedTarget(target);
        if (type == null || !StringUtils.hasText(code)) {
            return false;
        }

        long result = redisUtil.verifyAndConsumeCode(
                RedisKeys.verifyCode(type, normalizedTarget),
                RedisKeys.verifyAttempts(type, normalizedTarget),
                code.trim(),
                MAX_VERIFY_ATTEMPTS,
                Duration.ofMinutes(CODE_EXPIRE_MINUTES)
        );
        if (result == -2L) {
            log.warn("[VERIFICATION_CODE_LOCKED] channel=email type={} maxAttempts={}",
                    type.getCode(), MAX_VERIFY_ATTEMPTS);
        }
        return result == 1L;
    }

    @Override
    public void deleteCode(String target, VerificationCodeTypeEnum type) {
        if (!StringUtils.hasText(target) || type == null) {
            return;
        }
        String normalizedTarget = target.trim().toLowerCase(Locale.ROOT);
        redisUtil.delete(RedisKeys.verifyCode(type, normalizedTarget));
        redisUtil.delete(RedisKeys.verifyAttempts(type, normalizedTarget));
    }

    @Override
    public boolean canSend(String target, VerificationCodeTypeEnum type) {
        String normalizedTarget = requireSupportedTarget(target);
        if (type == null) {
            throw new BadRequestException("验证码类型不能为空");
        }
        String intervalKey = RedisKeys.verifyInterval(type, normalizedTarget);
        if (redisUtil.exists(intervalKey)) {
            return false;
        }

        String dailyKey = RedisKeys.verifyDailyCount(type, normalizedTarget);
        String countStr = redisUtil.getString(dailyKey);
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            if (count >= MAX_DAILY_SEND_COUNT) {
                throw new TooManyRequestsException("今日发送次数已达上限，请明天再试");
            }
        }

        return true;
    }

    private String requireSupportedTarget(String target) {
        if (!StringUtils.hasText(target)) {
            throw new BadRequestException("验证码接收邮箱不能为空");
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(AuthConstants.EMAIL_TARGET_SYMBOL)) {
            throw new BadRequestException("手机号验证码暂未开放，请使用邮箱");
        }
        return normalized;
    }

    private static Duration durationUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextDay = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, nextDay);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < AuthConstants.VERIFY_CODE_LENGTH; i++) {
            code.append(SECURE_RANDOM.nextInt(DECIMAL_RADIX));
        }
        return code.toString();
    }

}
