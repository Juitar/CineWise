package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalShowtimeSandboxImportServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate SHOW_DATE = LocalDate.of(2026, 8, 7);

    @Test
    void givenSandboxReference_whenPrepare_thenMapsAndCalculatesPlan() {
        ExternalShowtimeSandboxImportService service = service(
                new ExternalShowtimeQueryPort.QueryResult(List.of(snapshot(
                        ExternalShowtimeQueryPort.QualityStatus.SANDBOX_REFERENCE)), List.of(), false));

        ExternalShowtimeSandboxImportPlan plan = service.prepare(SHOW_DATE, List.of(201L));

        assertThat(plan.truncated()).isFalse();
        assertThat(plan.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.reference().externalShowId()).isEqualTo("show-1");
            assertThat(entry.reference().source()).isEqualTo("NETSTART_MAOYAN");
            assertThat(entry.reference().auditoriumText()).isEqualTo("1号厅");
            assertThat(entry.estimatedEndTime()).hasToString("2026-08-07T11:30");
        });
    }

    @Test
    void givenAcceptedCandidate_whenPrepareSandboxPlan_thenItIsNotUsedAsSandboxReference() {
        ExternalShowtimeSandboxImportService service = service(
                new ExternalShowtimeQueryPort.QueryResult(List.of(snapshot(
                        ExternalShowtimeQueryPort.QualityStatus.ACCEPTED)), List.of(), false));

        assertThat(service.prepare(SHOW_DATE, List.of(201L)).entries()).isEmpty();
    }

    @Test
    void givenTruncatedCandidates_whenPrepare_thenItCreatesNoPartialPlan() {
        ExternalShowtimeSandboxImportService service = service(
                new ExternalShowtimeQueryPort.QueryResult(List.of(snapshot(
                        ExternalShowtimeQueryPort.QualityStatus.SANDBOX_REFERENCE)), List.of(), true));

        ExternalShowtimeSandboxImportPlan plan = service.prepare(SHOW_DATE, List.of(201L));

        assertThat(plan.truncated()).isTrue();
        assertThat(plan.entries()).isEmpty();
    }

    @Test
    void givenSandboxCandidateWithoutExternalKey_whenPrepare_thenItIsSkippedWithoutFailure() {
        ExternalShowtimeQueryPort.ExternalShowtimeSnapshot incomplete = snapshot(
                ExternalShowtimeQueryPort.QualityStatus.SANDBOX_REFERENCE);
        incomplete = new ExternalShowtimeQueryPort.ExternalShowtimeSnapshot(
                incomplete.source(), incomplete.externalShowId(), incomplete.externalMovieId(),
                incomplete.externalCinemaId(), incomplete.movieId(), incomplete.cinemaId(), incomplete.startTime(),
                incomplete.endTime(), incomplete.listedPrice(), incomplete.durationMinutes(),
                incomplete.auditoriumText(),
                incomplete.priceSemantic(), incomplete.dataAt(), incomplete.expiresAt(), incomplete.isExpired(),
                incomplete.degraded(), incomplete.fallbackType(), incomplete.qualityStatus(),
                incomplete.rejectionCode(), null);
        ExternalShowtimeSandboxImportService service = service(
                new ExternalShowtimeQueryPort.QueryResult(List.of(incomplete), List.of(), false));

        assertThat(service.prepare(SHOW_DATE, List.of(201L)).entries()).isEmpty();
    }

    private static ExternalShowtimeSandboxImportService service(ExternalShowtimeQueryPort.QueryResult result) {
        ExternalShowtimeQueryPort port = query -> result;
        return new ExternalShowtimeSandboxImportService(
                port, new ExternalShowtimeSandboxReferencePolicy(FIXED_CLOCK));
    }

    private static ExternalShowtimeQueryPort.ExternalShowtimeSnapshot snapshot(
            ExternalShowtimeQueryPort.QualityStatus qualityStatus) {
        return new ExternalShowtimeQueryPort.ExternalShowtimeSnapshot(
                "NETSTART_MAOYAN", "show-1", "movie-1", "external-cinema-1", 101L, 201L,
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), null, new BigDecimal("36"), 90, "1号厅",
                ExternalShowtimeQueryPort.PriceSemantic.REFERENCE_ONLY,
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), false, false, null, qualityStatus, null,
                new ExternalShowtimeQueryPort.ExternalShowtimeKey(
                        "NETSTART_MAOYAN", "external-cinema-1", "show-1"));
    }
}
