package com.miaoyu.ticket.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.miaoyu.ticket.auth.application.VerificationEmailSender;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpVerificationEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpVerificationEmailSender sender = sender();

    @Test
    void shouldSendFixedPlainTextTemplate() {
        assertThat(sender.send(
                        "user@cinewise.test", "123456", VerificationPurpose.LOGIN, "trace-1"))
                .isEqualTo(VerificationEmailSender.DeliveryResult.SENT);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@cinewise.test");
        assertThat(captor.getValue().getTo()).containsExactly("user@cinewise.test");
        assertThat(captor.getValue().getText()).contains("登录", "123456", "5 分钟");
    }

    @Test
    void shouldMapAuthenticationFailureToDefiniteFailure() {
        doThrow(new MailAuthenticationException("rejected"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThat(sender.send(
                        "user@cinewise.test", "123456", VerificationPurpose.LOGIN, "trace-1"))
                .isEqualTo(VerificationEmailSender.DeliveryResult.FAILED);
    }

    @Test
    void shouldMapSendFailureToUnknownWithoutRetry() {
        doThrow(new MailSendException("unknown"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThat(sender.send(
                        "user@cinewise.test", "123456", VerificationPurpose.LOGIN, "trace-1"))
                .isEqualTo(VerificationEmailSender.DeliveryResult.UNKNOWN);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldRenderPasswordResetPurpose() {
        assertThat(sender.send(
                        "user@cinewise.test", "123456", VerificationPurpose.RESET_PASSWORD, "trace-1"))
                .isEqualTo(VerificationEmailSender.DeliveryResult.SENT);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("重置密码", "123456");
    }

    private SmtpVerificationEmailSender sender() {
        MailProperties mailProperties = new MailProperties();
        mailProperties.setHost("smtp.cinewise.test");
        return new SmtpVerificationEmailSender(mailSender, mailProperties, properties());
    }

    private VerificationCodeProperties properties() {
        return new VerificationCodeProperties(
                "test-verification-secret-at-least-32-bytes-long",
                6,
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                Duration.ofMinutes(5),
                10,
                new VerificationCodeProperties.Mail(
                        true, "no-reply@cinewise.test", "妙语购票邮箱验证码"));
    }
}
