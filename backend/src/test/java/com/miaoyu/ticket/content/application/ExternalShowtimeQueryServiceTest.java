package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExternalShowtimeQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate DATE = LocalDate.of(2026, 8, 7);

    @Test
    void givenMappedCandidate_whenProviderSucceeds_thenItSavesOnlyReferenceSnapshot() {
        MemorySnapshots snapshots = new MemorySnapshots();
        ExternalShowtimeQueryService service = service(new ExternalShowtimeProvider.FetchResult(List.of(
                new ExternalShowtimeProvider.Candidate("s1", "m1", "c1",
                        OffsetDateTime.parse("2026-08-07T11:00:00+08:00"), new BigDecimal("36"))), null), snapshots);

        ExternalShowtimeQueryPort.QueryResult result = service.query(
                new ExternalShowtimeQueryPort.Query(DATE, List.of(21L)));

        assertThat(result.snapshots()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.movieId()).isEqualTo(11L);
            assertThat(snapshot.cinemaId()).isEqualTo(21L);
            assertThat(snapshot.priceSemantic()).isEqualTo(ExternalShowtimeQueryPort.PriceSemantic.REFERENCE_ONLY);
            assertThat(snapshot.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-08-07T09:10:00+08:00"));
            assertThat(snapshot.degraded()).isFalse();
            assertThat(snapshot.qualityStatus()).isEqualTo(ExternalShowtimeQueryPort.QualityStatus.ACCEPTED);
            assertThat(snapshot.externalShowtimeKey()).isEqualTo(
                    new ExternalShowtimeQueryPort.ExternalShowtimeKey("NETSTART_MAOYAN", "c1", "s1"));
        });
        assertThat(snapshots.saved).isPresent();
    }

    @Test
    void givenProviderFailsAndFreshSnapshotExists_whenQuery_thenItReturnsLabeledSnapshot() {
        MemorySnapshots snapshots = new MemorySnapshots();
        ExternalShowtimeQueryPort.ExternalShowtimeSnapshot snapshot = snapshot();
        snapshots.saved = Optional.of(new ExternalShowtimeSnapshotPort.Snapshot(List.of(snapshot),
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"), OffsetDateTime.parse("2026-08-07T09:10:00+08:00")));
        ExternalShowtimeQueryService service = service(new ExternalShowtimeProvider.FetchResult(List.of(),
                ExternalShowtimeProvider.FailureCategory.NETWORK), snapshots);

        ExternalShowtimeQueryPort.QueryResult result = service.query(
                new ExternalShowtimeQueryPort.Query(DATE, List.of(21L)));

        assertThat(result.snapshots()).singleElement().satisfies(resultSnapshot -> {
            assertThat(resultSnapshot.degraded()).isTrue();
            assertThat(resultSnapshot.fallbackType()).isEqualTo(ExternalShowtimeQueryPort.FallbackType.SNAPSHOT);
        });
    }

    @Test
    void givenProviderFailsWithoutFreshSnapshot_whenQuery_thenItReturns303004InsteadOfEmptyResult() {
        ExternalShowtimeQueryService service = service(new ExternalShowtimeProvider.FetchResult(List.of(),
                ExternalShowtimeProvider.FailureCategory.RATE_LIMITED), new MemorySnapshots());

        assertThatThrownBy(() -> service.query(new ExternalShowtimeQueryPort.Query(DATE, List.of(21L))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    @Test
    void givenProviderFailsAndSnapshotExpired_whenQuery_thenItDoesNotReturnExpiredCandidate() {
        MemorySnapshots snapshots = new MemorySnapshots();
        snapshots.saved = Optional.of(new ExternalShowtimeSnapshotPort.Snapshot(List.of(snapshot()),
                OffsetDateTime.parse("2026-08-07T08:00:00+08:00"), OffsetDateTime.parse("2026-08-07T08:59:00+08:00")));
        ExternalShowtimeQueryService service = service(new ExternalShowtimeProvider.FetchResult(List.of(),
                ExternalShowtimeProvider.FailureCategory.TIMEOUT), snapshots);

        assertThatThrownBy(() -> service.query(new ExternalShowtimeQueryPort.Query(DATE, List.of(21L))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    @Test
    void givenUnmappedMovie_whenProviderSucceeds_thenItIsIsolatedInsteadOfGuessingByName() {
        MemorySnapshots snapshots = new MemorySnapshots();
        ExternalShowtimeQueryService service = new ExternalShowtimeQueryService(
                (date, cinemas) -> new ExternalShowtimeProvider.FetchResult(List.of(
                        new ExternalShowtimeProvider.Candidate(
                        "s1", "unknown", "c1", OffsetDateTime.parse("2026-08-07T11:00:00+08:00"), null)), null),
                (provider, type, ids) -> List.of(
                        new ContentExternalIdentityLookupPort.ExternalIdentity(21L, "c1", "70")),
                new ContentIdentityResolutionService((provider, type, ids) ->
                        new ContentIdentityResolutionPort.ResolutionBatch(
                        ids.stream().map(id -> new ContentIdentityResolutionPort.Resolution(id, null,
                        ContentIdentityResolutionPort.ResolutionStatus.NOT_FOUND)).toList())), snapshots, CLOCK);

        assertThat(service.query(new ExternalShowtimeQueryPort.Query(DATE, List.of(21L))).snapshots()).isEmpty();
    }

    @Test
    void givenDateOutsideSevenDays_whenQuery_thenItRejectsTheWholeRequest() {
        ExternalShowtimeQueryService service = service(
                new ExternalShowtimeProvider.FetchResult(List.of(), null), new MemorySnapshots());

        assertThatThrownBy(() -> service.query(new ExternalShowtimeQueryPort.Query(DATE.plusDays(8), List.of(21L))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(100001);
    }

    private static ExternalShowtimeQueryService service(ExternalShowtimeProvider.FetchResult fetched,
                                                         MemorySnapshots snapshots) {
        return new ExternalShowtimeQueryService((date, cinemas) -> fetched,
                (provider, type, ids) -> List.of(
                        new ContentExternalIdentityLookupPort.ExternalIdentity(21L, "c1", "70")),
                new ContentIdentityResolutionService((provider, type, ids) ->
                        new ContentIdentityResolutionPort.ResolutionBatch(
                        ids.stream().map(id -> new ContentIdentityResolutionPort.Resolution(id, 11L,
                                ContentIdentityResolutionPort.ResolutionStatus.RESOLVED)).toList())), snapshots, CLOCK);
    }

    private static ExternalShowtimeQueryPort.ExternalShowtimeSnapshot snapshot() {
        return new ExternalShowtimeQueryPort.ExternalShowtimeSnapshot("NETSTART_MAOYAN", "s1", "m1", "c1", 11L,
                21L, OffsetDateTime.parse("2026-08-07T11:00:00+08:00"), null, null,
                ExternalShowtimeQueryPort.PriceSemantic.REFERENCE_ONLY,
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T09:10:00+08:00"), false, false, null,
                ExternalShowtimeQueryPort.QualityStatus.ACCEPTED,
                new ExternalShowtimeQueryPort.ExternalShowtimeKey("NETSTART_MAOYAN", "c1", "s1"));
    }

    /** 内存快照让用例只验证 Application 决策，不依赖 MySQL 夹具。 */
    private static final class MemorySnapshots implements ExternalShowtimeSnapshotPort {
        private Optional<Snapshot> saved = Optional.empty();

        @Override public Optional<Snapshot> find(LocalDate showDate, List<Long> cinemaIds) { return saved; }
        @Override
        public void save(LocalDate showDate, List<Long> cinemaIds, Snapshot snapshot) {
            saved = Optional.of(snapshot);
        }
    }
}
