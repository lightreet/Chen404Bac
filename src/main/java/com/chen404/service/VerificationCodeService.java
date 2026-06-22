package com.chen404.service;

import com.chen404.domain.enums.VerificationCodeTypeEnum;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 生成并发送验证码
     *
     * @param target 目标（邮箱或手机号）
     * @param type   验证码业务类型
     * @return 验证码（仅用于测试，生产环境不返回）
     */
    String generateAndSendCode(String target, VerificationCodeTypeEnum type);

    /**
     * 验证验证码
     *
     * @param target 目标
     * @param type   验证码业务类型
     * @param code   验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String target, VerificationCodeTypeEnum type, String code);

    /**
     * 删除验证码
     *
     * @param target 目标
     * @param type   验证码业务类型
     */
    void deleteCode(String target, VerificationCodeTypeEnum type);

    /**
     * 检查是否可以发送（防频繁）
     *
     * @param target 目标
     * @param type   验证码业务类型
     * @return true-可以发送 false-太频繁
     */
    boolean canSend(String target, VerificationCodeTypeEnum type);
}
