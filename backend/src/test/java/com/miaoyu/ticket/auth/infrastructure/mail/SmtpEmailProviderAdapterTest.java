package com.miaoyu.ticket.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.miaoyu.ticket.auth.application.mail.DeliveryResultStatus;
import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.auth.application.mail.EmailProviderPort;
import com.miaoyu.ticket.auth.infrastructure.config.EmailDeliveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailProviderAdapterTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailDeliveryProperties properties = new EmailDeliveryProperties(
            true,
            "no-reply@cinewise.test",
            new EmailDeliveryProperties.Template("主题", "正文"));

    @Test
    void shouldSendSameDeliveryKeyOnlyOnceAndQueryResult() {
        SmtpEmailProviderAdapter adapter = new SmtpEmailProviderAdapter(mailSender, properties);

        assertThat(adapter.send(command()).status()).isEqualTo(DeliveryResultStatus.SENT);
        assertThat(adapter.send(command()).status()).isEqualTo(DeliveryResultStatus.SENT);
        assertThat(adapter.query("delivery-1").status()).isEqualTo(DeliveryResultStatus.SENT);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldMapDefiniteFailureAndUnknownWithoutRetrying() {
        doThrow(new MailAuthenticationException("rejected"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpEmailProviderAdapter failed = new SmtpEmailProviderAdapter(mailSender, properties);
        assertThat(failed.send(command()).status()).isEqualTo(DeliveryResultStatus.FAILED);

        JavaMailSender uncertainSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("timeout"))
                .when(uncertainSender)
                .send(any(SimpleMailMessage.class));
        SmtpEmailProviderAdapter uncertain = new SmtpEmailProviderAdapter(uncertainSender, properties);
        assertThat(uncertain.send(command()).status()).isEqualTo(DeliveryResultStatus.UNKNOWN);
        assertThat(uncertain.send(command()).status()).isEqualTo(DeliveryResultStatus.UNKNOWN);
        verify(uncertainSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldKeepUnknownForUnseenQueryAndFailClosedWithoutSenderAddress() {
        SmtpEmailProviderAdapter adapter = new SmtpEmailProviderAdapter(mailSender, properties);
        assertThat(adapter.query("missing")).isEqualTo(EmailDeliveryResult.unknown());

        SmtpEmailProviderAdapter invalid = new SmtpEmailProviderAdapter(
                mailSender,
                new EmailDeliveryProperties(
                        true, "", new EmailDeliveryProperties.Template("主题", "正文")));
        assertThat(invalid.send(command()).status()).isEqualTo(DeliveryResultStatus.FAILED);
    }

    private EmailProviderPort.ProviderEmail command() {
        return new EmailProviderPort.ProviderEmail(
                "delivery-1",
                "private-user@cinewise.test",
                "主题",
                "正文 secret-variable-value",
                "trace-1");
    }
}
