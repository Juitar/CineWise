package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ExternalShowtimeSandboxReferencePolicyTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private final ExternalShowtimeSandboxReferencePolicy policy =
            new ExternalShowtimeSandboxReferencePolicy(FIXED_CLOCK);

    @Test
    void givenEligibleFutureReference_whenPrepare_thenCalculateLocalEstimatedEndTime() {
        ExternalShowtimeSandboxPreparation result = policy.prepare(reference());

        assertThat(result.status()).isEqualTo(ExternalShowtimeSandboxPreparation.Status.READY);
        assertThat(result.estimatedEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 7, 11, 30));
    }

    @Test
    void givenExpiredReference_whenPrepare_thenSkipWithoutEstimatedEndTime() {
        ExternalShowtimeSandboxPreparation result = policy.prepare(withExpiresAt(
                OffsetDateTime.parse("2026-08-07T08:59:59+08:00")));

        assertThat(result.status()).isEqualTo(ExternalShowtimeSandboxPreparation.Status.EXPIRED);
        assertThat(result.estimatedEndTime()).isNull();
    }

    @Test
    void givenDegradedReference_whenPrepare_thenSkip() {
        ExternalShowtimeSandboxReference reference = new ExternalShowtimeSandboxReference(
                "NETSTART_MAOYAN", "cinema-1", "show-1", 101L, 201L,
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), 90, "3号厅",
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), true, false, true, false);

        assertThat(policy.prepare(reference).status())
                .isEqualTo(ExternalShowtimeSandboxPreparation.Status.DEGRADED);
    }

    @Test
    void givenMissingDuration_whenPrepare_thenSkip() {
        ExternalShowtimeSandboxReference reference = new ExternalShowtimeSandboxReference(
                "NETSTART_MAOYAN", "cinema-1", "show-1", 101L, 201L,
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), null, "3号厅",
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), true, false, false, false);

        assertThat(policy.prepare(reference).status())
                .isEqualTo(ExternalShowtimeSandboxPreparation.Status.INVALID_DURATION);
    }

    @Test
    void givenStartedReference_whenPrepare_thenSkip() {
        ExternalShowtimeSandboxReference reference = new ExternalShowtimeSandboxReference(
                "NETSTART_MAOYAN", "cinema-1", "show-1", 101L, 201L,
                OffsetDateTime.parse("2026-08-07T08:30:00+08:00"), 90, "3号厅",
                OffsetDateTime.parse("2026-08-07T07:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), true, false, false, false);

        assertThat(policy.prepare(reference).status())
                .isEqualTo(ExternalShowtimeSandboxPreparation.Status.ALREADY_STARTED);
    }

    private static ExternalShowtimeSandboxReference reference() {
        return new ExternalShowtimeSandboxReference(
                "NETSTART_MAOYAN", "cinema-1", "show-1", 101L, 201L,
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), 90, "3号厅",
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), true, false, false, false);
    }

    private static ExternalShowtimeSandboxReference withExpiresAt(OffsetDateTime expiresAt) {
        ExternalShowtimeSandboxReference reference = reference();
        return new ExternalShowtimeSandboxReference(
                reference.provider(), reference.externalCinemaId(), reference.externalShowId(), reference.movieId(),
                reference.cinemaId(), reference.startTime(), reference.durationMinutes(), reference.auditoriumText(),
                reference.dataAt(), expiresAt, reference.sandboxReferenceEligible(), reference.expired(),
                reference.degraded(), reference.fallback());
    }
}
