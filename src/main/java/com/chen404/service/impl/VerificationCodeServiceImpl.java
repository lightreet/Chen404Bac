package com.chen404.service.impl;

import com.chen404.service.EmailService;
import com.chen404.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EmailService emailService;

    // 验证码有效期：5分钟
    private static final long CODE_EXPIRE_MINUTES = 5;

    // 发送间隔：60秒
    private static final long SEND_INTERVAL_SECONDS = 60;

    // 每天最大发送次数
    private static final int MAX_DAILY_SEND_COUNT = 10;

    @Override
    public String generateAndSendCode(String target, String type) {
        // 检查发送频率
        if (!canSend(target, type)) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }

        // 生成6位数字验证码
        String code = generateCode();

        // 存储到Redis
        String codeKey = buildCodeKey(target, type);
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 记录发送次数
        recordSendCount(target, type);

        // 发送邮件
        if (target.contains("@")) {
            emailService.sendVerificationCode(target, code, type);
        }
        // 手机号短信发送（后续实现）

        log.info("验证码已发送至：{}，类型：{}，验证码：{}", target, type, code);
        return code;
    }

    @Override
    public boolean verifyCode(String target, String type, String code) {
        if (!target.contains("@")) {
            // 手机号验证码验证（后续实现）
            return true;
        }

        String codeKey = buildCodeKey(target, type);
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            return false;
        }

        boolean valid = storedCode.equals(code);
        if (valid) {
            // 验证成功后删除验证码
            redisTemplate.delete(codeKey);
        }

        return valid;
    }

    @Override
    public void deleteCode(String target, String type) {
        String codeKey = buildCodeKey(target, type);
        redisTemplate.delete(codeKey);
    }

    @Override
    public boolean canSend(String target, String type) {
        // 检查发送间隔
        String intervalKey = buildIntervalKey(target, type);
        Boolean hasKey = redisTemplate.hasKey(intervalKey);
        if (Boolean.TRUE.equals(hasKey)) {
            return false;
        }

        // 检查每日发送次数
        String dailyKey = buildDailyKey(target, type);
        String countStr = redisTemplate.opsForValue().get(dailyKey);
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
    private void recordSendCount(String target, String type) {
        // 记录发送间隔
        String intervalKey = buildIntervalKey(target, type);
        redisTemplate.opsForValue().set(intervalKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 记录每日次数
        String dailyKey = buildDailyKey(target, type);
        Long count = redisTemplate.opsForValue().increment(dailyKey);
        if (count != null && count == 1) {
            // 设置过期时间为当天剩余时间
            redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        }
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 构建验证码存储key
     */
    private String buildCodeKey(String target, String type) {
        return "verify:code:" + type + ":" + target;
    }

    /**
     * 构建发送间隔key
     */
    private String buildIntervalKey(String target, String type) {
        return "verify:interval:" + type + ":" + target;
    }

    /**
     * 构建每日次数key
     */
    private String buildDailyKey(String target, String type) {
        return "verify:daily:" + type + ":" + target;
    }
}
