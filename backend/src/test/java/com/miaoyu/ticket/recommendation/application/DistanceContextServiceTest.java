package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

    @Test
    void rejectsAnotherUserExpiredContextAndRepeatedUploadWithoutReplacingTheLocation() {
        CurrentUserAccessor users = Mockito.mock(CurrentUserAccessor.class);
        when(users.requireCurrentUserId()).thenReturn(7L);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-06T10:00:00Z"));
        DistanceContextService service = new DistanceContextService(users, clock);

        var ownerContext = service.create("run-owner");
        when(users.requireCurrentUserId()).thenReturn(8L);
        assertThat(service.upload(ownerContext.distanceContextId(), new BigDecimal("113.000000"),
                new BigDecimal("28.000000"))).isFalse();

        when(users.requireCurrentUserId()).thenReturn(7L);
        assertThat(service.upload(ownerContext.distanceContextId(), new BigDecimal("112.938814"),
                new BigDecimal("28.228209"))).isTrue();
        assertThat(service.upload(ownerContext.distanceContextId(), new BigDecimal("113.000000"),
                new BigDecimal("28.000000"))).isFalse();
        var coordinate = service.consume(ownerContext.distanceContextId(), "run-owner");
        assertThat(coordinate.longitude()).isEqualByComparingTo("112.938814");
        assertThat(coordinate.latitude()).isEqualByComparingTo("28.228209");

        var expiredContext = service.create("run-expired");
        clock.advance(Duration.ofSeconds(301));
        assertThat(service.upload(expiredContext.distanceContextId(), new BigDecimal("112.938814"),
                new BigDecimal("28.228209"))).isFalse();
        assertThat(service.consume(expiredContext.distanceContextId(), "run-expired")).isNull();
    }

    /** 让同一测试同时覆盖创建时刻和过期后的上传，避免依赖真实系统时间。 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
