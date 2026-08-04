package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAuditCleanupServiceTest {

    @Test
    void shouldDeleteOnlyLogsOlderThanConfiguredRetention() {
        LoginAuditRepository repository = mock(LoginAuditRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T08:00:00Z"), ZoneOffset.UTC);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 4, 8, 0);
        when(repository.deleteCreatedBefore(cutoff)).thenReturn(4);

        int deleted = new LoginAuditCleanupService(repository, clock).deleteExpiredLogs(30);

        assertThat(deleted).isEqualTo(4);
        verify(repository).deleteCreatedBefore(cutoff);
    }
}
