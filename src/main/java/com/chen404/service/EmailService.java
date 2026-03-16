package com.chen404.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     *
     * @param toEmail 目标邮箱
     * @param code    验证码
     * @param type    类型：register-注册 reset-重置密码
     */
    void sendVerificationCode(String toEmail, String code, String type);

    /**
     * 发送普通邮件
     *
     * @param toEmail 目标邮箱
     * @param subject 主题
     * @param content 内容
     */
    void sendEmail(String toEmail, String subject, String content);
}
