package com.chen404.service.impl;

import com.chen404.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

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
        log.info("验证码邮件已发送至：{}，类型：{}，验证码：{}", toEmail, type, code);
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
    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("发送HTML邮件失败：", e);
            throw new RuntimeException("邮件发送失败");
        }
    }

    /**
     * 构建验证码邮件模板
     */
    private String buildVerificationEmail(String typeName, String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #fb7299, #ff9a9e); padding: 30px; text-align: center; }
                    .header h1 { color: white; margin: 0; font-size: 24px; }
                    .content { padding: 40px 30px; }
                    .code-box { background-color: #f8f9fa; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0; }
                    .code { font-size: 32px; font-weight: bold; color: #fb7299; letter-spacing: 5px; }
                    .info { color: #666; line-height: 1.6; }
                    .footer { background-color: #f8f9fa; padding: 20px; text-align: center; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Chen404 博客</h1>
                    </div>
                    <div class="content">
                        <p class="info">您好！</p>
                        <p class="info">感谢您使用 Chen404 博客。您正在进行<strong>%s</strong>操作，验证码如下：</p>
                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>
                        <p class="info">验证码有效期为 <strong>5 分钟</strong>，请勿泄露给他人。</p>
                        <p class="info" style="color: #999; font-size: 12px;">如非本人操作，请忽略此邮件。</p>
                    </div>
                    <div class="footer">
                        <p>Chen404 博客团队</p>
                        <p>此邮件由系统自动发送，请勿回复</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(typeName, code);
    }
}
