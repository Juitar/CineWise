package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DistanceContextServiceTest {
    @Test
    void acceptsOwnerLocationAndConsumesItOnlyOnce() {
        CurrentUserAccessor users = Mockito.mock(CurrentUserAccessor.class);
        when(users.requireCurrentUserId()).thenReturn(7L);
        DistanceContextService service = new DistanceContextService(users,
                Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC));
        var context = service.create("run-1");
        assertThat(service.upload(context.distanceContextId(), new BigDecimal("112.938814"),
                new BigDecimal("28.228209"))).isTrue();
        assertThat(service.consume(context.distanceContextId(), "run-1")).isNotNull();
        assertThat(service.consume(context.distanceContextId(), "run-1")).isNull();
    }
}
