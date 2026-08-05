package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

class SaleableShowBatchQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    @Test
    void givenDuplicateCinemaIds_whenQuery_thenDeduplicateAndReturnSnapshotFacts() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        when(repository.findSaleableShowsByCinemaIds(any())).thenReturn(List.of(
                snapshot(101L, 11L, 21L, LocalDateTime.of(2026, 8, 5, 11, 0)),
                snapshot(102L, 12L, 22L, LocalDateTime.of(2026, 8, 5, 12, 0)),
                snapshot(103L, 13L, 22L, LocalDateTime.of(2026, 8, 5, 13, 0))));
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, List.of(21L, 22L, 21L)));

        assertThat(result.truncated()).isFalse();
        assertThat(result.shows()).extracting(SaleableShowView::showId).containsExactly(101L, 102L, 103L);
        assertThat(result.shows()).allSatisfy(show -> {
            assertThat(show.price()).isEqualByComparingTo("49.90");
            assertThat(show.price().scale()).isEqualTo(2);
            assertThat(show.dataType()).isEqualTo("MOCK");
            assertThat(show.source()).isEqualTo("unit-test-seed");
            assertThat(show.dataAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
            assertThat(show.expiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 1));
            assertThat(show.saleable()).isTrue();
        });
        ArgumentCaptor<ShowQueryRepository.BatchQueryCriteria> captor =
                ArgumentCaptor.forClass(ShowQueryRepository.BatchQueryCriteria.class);
        verify(repository).findSaleableShowsByCinemaIds(captor.capture());
        assertThat(captor.getValue().cinemaIds()).containsExactly(21L, 22L);
        assertThat(captor.getValue().startsAfter()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
        assertThat(captor.getValue().dateStart()).isEqualTo(TODAY.atStartOfDay());
        assertThat(captor.getValue().dateEnd()).isEqualTo(TODAY.plusDays(1).atStartOfDay());
        assertThat(captor.getValue().fetchLimit()).isEqualTo(201);
    }

    @Test
    void givenEmptyCinemaIds_whenQuery_thenReturnEmptyWithoutRepositoryAccess() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, List.of()));

        assertThat(result.shows()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(repository, never()).findSaleableShowsByCinemaIds(any());
    }

    @Test
    void givenExactlyOneHundredCinemaIds_whenQuery_thenAcceptBoundedBatch() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        when(repository.findSaleableShowsByCinemaIds(any())).thenReturn(List.of());
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);
        List<Long> cinemaIds = new ArrayList<>();
        for (long cinemaId = 1; cinemaId <= 100; cinemaId++) {
            cinemaIds.add(cinemaId);
        }

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, cinemaIds));

        assertThat(result.shows()).isEmpty();
        ArgumentCaptor<ShowQueryRepository.BatchQueryCriteria> captor =
                ArgumentCaptor.forClass(ShowQueryRepository.BatchQueryCriteria.class);
        verify(repository).findSaleableShowsByCinemaIds(captor.capture());
        assertThat(captor.getValue().cinemaIds()).hasSize(100);
    }

    @Test
    void givenMoreThanTwoHundredSaleableShows_whenQuery_thenMarkTruncatedWithoutSilentLoss() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        List<ShowQueryRepository.ShowSnapshot> snapshots = new ArrayList<>();
        for (long showId = 1; showId <= 201; showId++) {
            snapshots.add(snapshot(showId, showId, 21L, LocalDateTime.of(2026, 8, 5, 11, 0)));
        }
        when(repository.findSaleableShowsByCinemaIds(any())).thenReturn(snapshots);
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, List.of(21L)));

        assertThat(result.shows()).hasSize(200);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void givenInvalidQueries_whenQuery_thenReturnInvalidParameter() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);
        List<Long> tooManyCinemas = new ArrayList<>();
        for (long cinemaId = 1; cinemaId <= 101; cinemaId++) {
            tooManyCinemas.add(cinemaId);
        }
        List<SaleableShowBatchQuery> invalidQueries = List.of(
                new SaleableShowBatchQuery(null, List.of(1L)),
                new SaleableShowBatchQuery(TODAY, List.of(0L)),
                new SaleableShowBatchQuery(TODAY, tooManyCinemas),
                new SaleableShowBatchQuery(TODAY.minusDays(1), List.of(1L)),
                new SaleableShowBatchQuery(TODAY.plusDays(7), List.of(1L)));

        for (SaleableShowBatchQuery query : invalidQueries) {
            assertThatThrownBy(() -> service.query(query))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        }
        verify(repository, never()).findSaleableShowsByCinemaIds(any());
    }

    @Test
    void givenRepositoryUnavailable_whenQuery_thenReturnTicketingQueryUnavailable() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        when(repository.findSaleableShowsByCinemaIds(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.query(
                new SaleableShowBatchQuery(TODAY, List.of(21L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TicketingErrorCode.QUERY_UNAVAILABLE));
        assertThat(TicketingErrorCode.QUERY_UNAVAILABLE.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void givenShowStartingBeforeSnapshotTtl_whenQuery_thenExpireAtShowStart() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        LocalDateTime showStartTime = LocalDateTime.of(2026, 8, 5, 10, 0, 30);
        when(repository.findSaleableShowsByCinemaIds(any()))
                .thenReturn(List.of(snapshot(104L, 14L, 21L, showStartTime)));
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, List.of(21L)));

        // 开场早于快照 60 秒窗口时，开场时间是候选的更严格边界。
        assertThat(result.shows()).singleElement()
                .extracting(SaleableShowView::expiresAt)
                .isEqualTo(showStartTime);
    }

    private ShowQueryRepository.ShowSnapshot snapshot(
            long showId,
            long movieId,
            long cinemaId,
            LocalDateTime startTime) {
        return new ShowQueryRepository.ShowSnapshot(
                showId,
                movieId,
                cinemaId,
                301L,
                "一号厅",
                startTime,
                startTime.plusHours(2),
                "国语2D",
                new BigDecimal("49.90"),
                10,
                "ON_SALE",
                "MOCK",
                "unit-test-seed",
                3,
                startTime.minusHours(1));
    }
}
