package com.miaoyu.ticket.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class EmailDeliveryApplicationServiceTest {

    private final AuthUserRepository userRepository = mock(AuthUserRepository.class);
    private final EmailTemplateRegistry templateRegistry = mock(EmailTemplateRegistry.class);
    private final EmailProviderPort provider = mock(EmailProviderPort.class);
    private final EmailDeliveryApplicationService service =
            new EmailDeliveryApplicationService(userRepository, templateRegistry, provider);

    @Test
    void shouldResolveVerifiedEmailInsideAuthAndReturnTypedResult() {
        EmailDeliveryCommand command = command(Map.of("movieTitle", "测试影片"));
        when(templateRegistry.render(command.templateCode(), command.variables()))
                .thenReturn(Optional.of(new EmailTemplateRegistry.RenderedEmail("主题", "正文")));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(true, AccountStatus.NORMAL)));
        when(provider.send(any())).thenReturn(EmailDeliveryResult.sent("message-1"));

        assertThat(service.send(command)).isEqualTo(EmailDeliveryResult.sent("message-1"));
        verify(provider).send(new EmailProviderPort.ProviderEmail(
                "VIEWING_REMINDER:1:0:BEFORE",
                "private-user@cinewise.test",
                "主题",
                "正文",
                "trace-1"));
    }

    @Test
    void shouldRejectUnknownUnverifiedAndDisabledUsersWithoutProviderCall() {
        EmailDeliveryCommand command = command(Map.of());
        when(templateRegistry.render(any(), any()))
                .thenReturn(Optional.of(new EmailTemplateRegistry.RenderedEmail("主题", "正文")));

        when(userRepository.findById(1001L)).thenReturn(Optional.empty());
        assertThat(service.send(command).errorCode())
                .isEqualTo(EmailDeliveryApplicationService.RECIPIENT_UNAVAILABLE);

        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(false, AccountStatus.NORMAL)));
        assertThat(service.send(command).errorCode())
                .isEqualTo(EmailDeliveryApplicationService.RECIPIENT_UNAVAILABLE);

        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(true, AccountStatus.DISABLED)));
        assertThat(service.send(command).errorCode())
                .isEqualTo(EmailDeliveryApplicationService.RECIPIENT_UNAVAILABLE);
        verify(provider, never()).send(any());
    }

    @Test
    void shouldRejectInvalidCommandAndTemplateBeforeResolvingUser() {
        EmailDeliveryCommand invalid = new EmailDeliveryCommand(
                "bad key", "VIEWING_REMINDER", "1001", Map.of(), "trace-1");
        assertThat(service.send(invalid).errorCode())
                .isEqualTo(EmailDeliveryApplicationService.INVALID_COMMAND);

        EmailDeliveryCommand command = command(Map.of());
        when(templateRegistry.render(any(), any())).thenReturn(Optional.empty());
        assertThat(service.send(command).errorCode())
                .isEqualTo(EmailDeliveryApplicationService.TEMPLATE_REJECTED);
        verify(userRepository, never()).findById(1001L);
    }

    @Test
    void shouldQueryOnlyWithOriginalValidDeliveryKey() {
        when(provider.query("VIEWING_REMINDER:1:0:BEFORE"))
                .thenReturn(EmailDeliveryResult.unknown());

        assertThat(service.query("VIEWING_REMINDER:1:0:BEFORE"))
                .isEqualTo(EmailDeliveryResult.unknown());
        assertThat(service.query("bad key").errorCode())
                .isEqualTo(EmailDeliveryApplicationService.INVALID_COMMAND);
    }

    @Test
    void shouldNotLogRecipientTemplateValuesOrBody() {
        String sensitiveValue = "sensitive-variable-value";
        EmailDeliveryCommand command = new EmailDeliveryCommand(
                "VIEWING_REMINDER:secret-key:0:BEFORE",
                "VIEWING_REMINDER",
                "1001",
                Map.of("movieTitle", sensitiveValue),
                "trace-1");
        when(templateRegistry.render(command.templateCode(), command.variables()))
                .thenReturn(Optional.of(new EmailTemplateRegistry.RenderedEmail("secret-subject", "secret-body")));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(true, AccountStatus.NORMAL)));
        when(provider.send(any())).thenReturn(EmailDeliveryResult.sent("message-1"));
        Logger logger = (Logger) LoggerFactory.getLogger(EmailDeliveryApplicationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.send(command);
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(logs)
                .doesNotContain(
                        "private-user@cinewise.test",
                        sensitiveValue,
                        "secret-subject",
                        "secret-body",
                        command.deliveryKey());
    }

    private EmailDeliveryCommand command(Map<String, String> variables) {
        return new EmailDeliveryCommand(
                "VIEWING_REMINDER:1:0:BEFORE",
                "VIEWING_REMINDER",
                "1001",
                variables,
                "trace-1");
    }

    private AuthUser user(boolean verified, AccountStatus status) {
        return new AuthUser(
                1001L,
                "private-user@cinewise.test",
                "hash",
                "用户",
                RoleCode.USER,
                status,
                verified,
                0,
                "2026-08-03",
                LocalDateTime.of(2026, 8, 3, 8, 0));
    }
}
