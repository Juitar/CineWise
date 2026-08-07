package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.mail.EmailDeliveryPort;
import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TravelNotificationServiceTest {
    @Test
    void givenUnknownDelivery_whenRetrying_thenQueryWithoutResend() {
        TravelNotificationRepository repository = mock(TravelNotificationRepository.class);
        EmailDeliveryPort port = mock(EmailDeliveryPort.class);
        TravelTaskRepository.TravelTaskSnapshot task = task();
        String key = "VIEWING_REMINDER:90001:1:VIEWING_REMINDER";
        when(repository.findByDeliveryKey(key)).thenReturn(Optional.of(
                new TravelNotificationRepository.Notification(1, 1, 1, key, "UNKNOWN")));
        when(port.query(key)).thenReturn(EmailDeliveryResult.sent("m1"));
        Clock clock = Clock.fixed(java.time.Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC);
        new TravelNotificationService(repository, port, () -> 2L, clock).deliver(task, 1);
        verify(port, never()).send(any());
        verify(repository).markNotified(eq(1L), any());
    }

    @Test
    void givenNewDelivery_whenProviderSends_thenMarkSentAndNotifyTask() {
        TravelNotificationRepository repository = mock(TravelNotificationRepository.class);
        EmailDeliveryPort port = mock(EmailDeliveryPort.class);
        when(repository.findByDeliveryKey(any())).thenReturn(Optional.empty());
        when(repository.markSending(any(), any())).thenReturn(true);
        when(port.send(any())).thenReturn(EmailDeliveryResult.sent("m1"));
        new TravelNotificationService(repository, port, () -> 2L, Clock.systemUTC()).deliver(task(), 1);
        verify(port).send(any());
        verify(repository).markNotified(eq(1L), any());
    }

    private TravelTaskRepository.TravelTaskSnapshot task() {
        return new TravelTaskRepository.TravelTaskSnapshot(1, "90001", 7, 8, 9, "西湖区",
                LocalDateTime.now(), LocalDateTime.now(), 0, 0, TravelTaskStatus.READY, null, LocalDateTime.now());
    }
}
