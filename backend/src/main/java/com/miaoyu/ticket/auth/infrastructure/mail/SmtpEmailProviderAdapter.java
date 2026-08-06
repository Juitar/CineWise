package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.auth.application.mail.EmailProviderPort;
import com.miaoyu.ticket.auth.infrastructure.config.EmailDeliveryProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 公共端口复用 Spring Boot 的唯一 JavaMailSender；SMTP 无查询能力时只保留进程内结果。 */
@Component
@ConditionalOnExpression("'${cinewise.auth.mail-delivery.enabled:false}' == 'true'"
        + " && '${cinewise.auth.mail-delivery.provider:smtp}' == 'smtp'")
public class SmtpEmailProviderAdapter implements EmailProviderPort {

    static final int PROVIDER_REJECTED = 301104;

    private final JavaMailSender mailSender;
    private final EmailDeliveryProperties properties;
    private final ConcurrentMap<String, EmailDeliveryResult> knownResults = new ConcurrentHashMap<>();

    public SmtpEmailProviderAdapter(JavaMailSender mailSender, EmailDeliveryProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public EmailDeliveryResult send(ProviderEmail command) {
        return knownResults.computeIfAbsent(command.deliveryKey(), ignored -> deliver(command));
    }

    @Override
    public EmailDeliveryResult query(String deliveryKey) {
        return knownResults.getOrDefault(deliveryKey, EmailDeliveryResult.unknown());
    }

    private EmailDeliveryResult deliver(ProviderEmail command) {
        if (properties.from() == null || properties.from().isBlank()) {
            return EmailDeliveryResult.failed(PROVIDER_REJECTED);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(command.recipientEmail());
        message.setSubject(command.subject());
        message.setText(command.body());
        try {
            mailSender.send(message);
            return EmailDeliveryResult.sent(null);
        } catch (MailAuthenticationException | MailParseException exception) {
            return EmailDeliveryResult.failed(PROVIDER_REJECTED);
        } catch (MailSendException exception) {
            return EmailDeliveryResult.unknown();
        } catch (MailException exception) {
            return EmailDeliveryResult.unknown();
        }
    }
}
