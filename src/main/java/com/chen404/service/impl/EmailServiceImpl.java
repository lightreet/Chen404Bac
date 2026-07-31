package com.chen404.service.impl;

import com.chen404.domain.enums.VerificationCodeTypeEnum;
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
    private static final String DEFAULT_VERIFICATION_SUBJECT = "Chen404博客 - 验证码";
    private static final String DEFAULT_VERIFICATION_DISPLAY_NAME = "验证";

    private final JavaMailSender mailSender;
    private final MailTemplateSupport mailTemplateSupport;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailServiceImpl(JavaMailSender mailSender, MailTemplateSupport mailTemplateSupport) {
        this.mailSender = mailSender;
        this.mailTemplateSupport = mailTemplateSupport;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, VerificationCodeTypeEnum type) {
        VerificationCodeTypeEnum resolvedType = type == null ? null : type;
        String subject = resolvedType == null ? DEFAULT_VERIFICATION_SUBJECT : resolvedType.getMailSubject();
        String typeName = resolvedType == null ? DEFAULT_VERIFICATION_DISPLAY_NAME : resolvedType.getDisplayName();
        // 构建HTML邮件内容
        String htmlContent = buildVerificationEmail(typeName, code);

        sendHtmlEmail(toEmail, subject, htmlContent);
        log.info("验证码邮件已发送至：{}，类型：{}", toEmail, resolvedType == null ? "unknown" : resolvedType.getCode());
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
            log.error("[MAIL_SEND_FAIL] channel=email subjectPresent={}",
                    subject != null && !subject.isBlank(), e);
            throw new IllegalStateException("邮件发送失败", e);
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
