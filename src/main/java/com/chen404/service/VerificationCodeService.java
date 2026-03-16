package com.chen404.service;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 生成并发送验证码
     *
     * @param target 目标（邮箱或手机号）
     * @param type   类型：register-注册 login-登录 reset-重置密码
     * @return 验证码（仅用于测试，生产环境不返回）
     */
    String generateAndSendCode(String target, String type);

    /**
     * 验证验证码
     *
     * @param target 目标
     * @param type   类型
     * @param code   验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String target, String type, String code);

    /**
     * 删除验证码
     *
     * @param target 目标
     * @param type   类型
     */
    void deleteCode(String target, String type);

    /**
     * 检查是否可以发送（防频繁）
     *
     * @param target 目标
     * @param type   类型
     * @return true-可以发送 false-太频繁
     */
    boolean canSend(String target, String type);
}
