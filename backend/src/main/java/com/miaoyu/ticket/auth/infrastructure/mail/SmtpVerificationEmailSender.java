package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.VerificationEmailSender;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP 认证邮件使用固定纯文本模板，不记录收件人或验证码。 */
@Component
@ConditionalOnProperty(prefix = "cinewise.auth.verification.mail", name = "smtp-enabled", havingValue = "true")
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final VerificationCodeProperties properties;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender, MailProperties mailProperties, VerificationCodeProperties properties) {
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            throw new IllegalStateException("启用 SMTP 验证码邮件时必须配置 spring.mail.host");
        }
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public DeliveryResult send(
            String normalizedEmail, String code, VerificationPurpose purpose, String traceId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(normalizedEmail);
        message.setSubject(properties.mail().subject());
        message.setText(body(code, purpose));
        try {
            mailSender.send(message);
            return DeliveryResult.SENT;
        } catch (MailAuthenticationException | MailParseException exception) {
            return DeliveryResult.FAILED;
        } catch (MailSendException exception) {
            return DeliveryResult.UNKNOWN;
        } catch (MailException exception) {
            return DeliveryResult.UNKNOWN;
        }
    }

    private String body(String code, VerificationPurpose purpose) {
        String action = purpose == VerificationPurpose.LOGIN ? "登录" : "注册";
        return "您正在进行妙语购票" + action + "，验证码为：" + code + "。验证码 "
                + properties.ttl().toMinutes() + " 分钟内有效，请勿转发。";
    }
}
