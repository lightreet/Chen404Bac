package com.chen404.service;

import com.chen404.domain.enums.VerificationCodeTypeEnum;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     *
     * @param toEmail 目标邮箱
     * @param code    验证码
     * @param type    验证码业务类型
     */
    void sendVerificationCode(String toEmail, String code, VerificationCodeTypeEnum type);

    /**
     * 发送普通邮件
     *
     * @param toEmail 目标邮箱
     * @param subject 主题
     * @param content 内容
     */
    void sendEmail(String toEmail, String subject, String content);

    /**
     * 鍙戦€丠TML 閭欢
     */
    void sendHtmlEmail(String toEmail, String subject, String htmlContent);
}
