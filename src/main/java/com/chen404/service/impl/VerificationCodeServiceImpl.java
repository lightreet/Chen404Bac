package com.chen404.service.impl;

import com.chen404.domain.enums.VerificationCodeTypeEnum;
import com.chen404.service.EmailService;
import com.chen404.service.VerificationCodeService;
import com.chen404.util.AuthConstants;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final long CODE_EXPIRE_MINUTES = AuthConstants.SEND_CODE_EXPIRE_SECONDS / 60L;
    private static final long SEND_INTERVAL_SECONDS = AuthConstants.SEND_CODE_INTERVAL_SECONDS;
    private static final int MAX_DAILY_SEND_COUNT = AuthConstants.MAX_DAILY_SEND_CODE_COUNT;
    private static final int DECIMAL_RADIX = 10;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private EmailService emailService;

    @Override
    public String generateAndSendCode(String target, VerificationCodeTypeEnum type) {
        // 检查发送频率
        if (!canSend(target, type)) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }

        // 生成6位数字验证码
        String code = generateCode();

        // 存储到Redis
        String codeKey = RedisKeys.verifyCode(type, target);
        redisUtil.setString(codeKey, code, Duration.ofMinutes(CODE_EXPIRE_MINUTES));

        // 记录发送次数
        recordSendCount(target, type);

        // 发送邮件
        if (target.contains(AuthConstants.EMAIL_TARGET_SYMBOL)) {
            emailService.sendVerificationCode(target, code, type);
        }
        // 手机号短信发送（后续实现）

        log.info("验证码已发送至：{}，类型：{}", target, type.getCode());
        return code;
    }

    @Override
    public boolean verifyCode(String target, VerificationCodeTypeEnum type, String code) {
        if (!target.contains(AuthConstants.EMAIL_TARGET_SYMBOL)) {
            // 手机号验证码验证（后续实现）
            return true;
        }

        String codeKey = RedisKeys.verifyCode(type, target);
        String storedCode = redisUtil.getString(codeKey);

        if (storedCode == null) {
            return false;
        }

        boolean valid = storedCode.equals(code);
        if (valid) {
            // 验证成功后删除验证码
            redisUtil.delete(codeKey);
        }

        return valid;
    }

    @Override
    public void deleteCode(String target, VerificationCodeTypeEnum type) {
        String codeKey = RedisKeys.verifyCode(type, target);
        redisUtil.delete(codeKey);
    }

    @Override
    public boolean canSend(String target, VerificationCodeTypeEnum type) {
        // 检查发送间隔
        String intervalKey = RedisKeys.verifyInterval(type, target);
        if (redisUtil.exists(intervalKey)) {
            return false;
        }

        // 检查每日发送次数
        String dailyKey = RedisKeys.verifyDailyCount(type, target);
        String countStr = redisUtil.getString(dailyKey);
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            if (count >= MAX_DAILY_SEND_COUNT) {
                throw new RuntimeException("今日发送次数已达上限，请明天再试");
            }
        }

        return true;
    }

    /**
     * 记录发送次数
     */
    private void recordSendCount(String target, VerificationCodeTypeEnum type) {
        // 记录发送间隔
        String intervalKey = RedisKeys.verifyInterval(type, target);
        redisUtil.setString(intervalKey, AuthConstants.REDIS_FLAG_VALUE, Duration.ofSeconds(SEND_INTERVAL_SECONDS));

        // 记录每日次数
        String dailyKey = RedisKeys.verifyDailyCount(type, target);
        Long count = redisUtil.increment(dailyKey);
        if (count != null && count == 1) {
            // 设置过期到当天结束（更符合 “daily” 语义）
            redisUtil.expire(dailyKey, durationUntilEndOfDay());
        }
    }

    private static Duration durationUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.toLocalDate().atTime(23, 59, 59);
        long seconds = end.atZone(ZoneId.systemDefault()).toEpochSecond() - now.atZone(ZoneId.systemDefault()).toEpochSecond();
        return Duration.ofSeconds(Math.max(seconds, 1));
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < AuthConstants.VERIFY_CODE_LENGTH; i++) {
            code.append(ThreadLocalRandom.current().nextInt(DECIMAL_RADIX));
        }
        return code.toString();
    }

}
