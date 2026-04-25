package com.chen404.service.impl;

import com.chen404.service.support.MailTemplateSupport;
import com.chen404.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final String VERIFICATION_TEMPLATE = "mail/verification-code.html";
    private static final String DEFAULT_ACTION_HINT = "验证码有效期为 5 分钟，请勿泄露给他人。";
    private static final String DEFAULT_IGNORE_HINT = "如非本人操作，请忽略此邮件。";

    private final JavaMailSender mailSender;
    private final MailTemplateSupport mailTemplateSupport;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailServiceImpl(JavaMailSender mailSender, MailTemplateSupport mailTemplateSupport) {
        this.mailSender = mailSender;
        this.mailTemplateSupport = mailTemplateSupport;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, String type) {
        String subject;
        String typeName;

        switch (type) {
            case "register":
                subject = "Chen404博客 - 注册验证码";
                typeName = "注册";
                break;
            case "reset":
                subject = "Chen404博客 - 密码重置验证码";
                typeName = "密码重置";
                break;
            case "login":
                subject = "Chen404博客 - 登录验证码";
                typeName = "登录";
                break;
            default:
                subject = "Chen404博客 - 验证码";
                typeName = "验证";
        }

        // 构建HTML邮件内容
        String htmlContent = buildVerificationEmail(typeName, code);

        sendHtmlEmail(toEmail, subject, htmlContent);
        log.info("验证码邮件已发送至：{}，类型：{}", toEmail, type);
    }

    @Override
    public void sendEmail(String toEmail, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(content);

        mailSender.send(message);
        log.info("邮件已发送至：{}，主题：{}", toEmail, subject);
    }

    /**
     * 发送HTML邮件
     */
    @Override
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("发送HTML邮件失败: toEmail={}", toEmail, e);
            throw new RuntimeException("邮件发送失败");
        }
    }

    /**
     * 构建验证码邮件模板
     */
    private String buildVerificationEmail(String typeName, String code) {
        Map<String, String> variables = new LinkedHashMap<>(mailTemplateSupport.buildBrandVariables());
        variables.put("mailEyebrow", "身份验证");
        variables.put("mailTitle", typeName + "验证码");
        variables.put("mailLead", "你正在进行" + mailTemplateSupport.safeText(typeName) + "操作，请使用下方验证码完成验证。");
        variables.put("codeValue", mailTemplateSupport.safeText(code));
        variables.put("actionHint", DEFAULT_ACTION_HINT);
        variables.put("ignoreHint", DEFAULT_IGNORE_HINT);
        return mailTemplateSupport.render(VERIFICATION_TEMPLATE, variables);
    }
}
