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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class SaleableShowBatchQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    @Test
    void givenDuplicateCinemasAndMoreRowsThanLimit_whenQuery_thenDeduplicateAndMarkTruncated() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        when(repository.findSaleableShowsByCinemaIds(any())).thenReturn(List.of(
                snapshot(101L, 11L, 21L, LocalDateTime.of(2026, 8, 5, 11, 0)),
                snapshot(102L, 12L, 22L, LocalDateTime.of(2026, 8, 5, 12, 0)),
                snapshot(103L, 13L, 22L, LocalDateTime.of(2026, 8, 5, 13, 0))));
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(new SaleableShowBatchQuery(
                TODAY,
                List.of(21L, 22L, 21L),
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                2));

        assertThat(result.truncated()).isTrue();
        assertThat(result.shows()).extracting(SaleableShowView::showId).containsExactly(101L, 102L);
        assertThat(result.shows()).allSatisfy(show -> {
            assertThat(show.price()).isEqualByComparingTo("49.90");
            assertThat(show.price().scale()).isEqualTo(2);
            assertThat(show.expiresAt()).isEqualTo(show.startTime());
            assertThat(show.source()).isEqualTo("MOCK");
            assertThat(show.saleable()).isTrue();
        });
        ArgumentCaptor<ShowQueryRepository.BatchQueryCriteria> captor =
                ArgumentCaptor.forClass(ShowQueryRepository.BatchQueryCriteria.class);
        verify(repository).findSaleableShowsByCinemaIds(captor.capture());
        assertThat(captor.getValue().cinemaIds()).containsExactly(21L, 22L);
        assertThat(captor.getValue().startsAfter()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
        assertThat(captor.getValue().dateStart()).isEqualTo(TODAY.atStartOfDay());
        assertThat(captor.getValue().dateEnd()).isEqualTo(TODAY.plusDays(1).atStartOfDay());
        assertThat(captor.getValue().fetchLimit()).isEqualTo(3);
    }

    @Test
    void givenEmptyCinemaIds_whenQuery_thenReturnEmptyWithoutRepositoryAccess() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);

        SaleableShowBatchResult result = service.query(
                new SaleableShowBatchQuery(TODAY, List.of(), null, null, null));

        assertThat(result.shows()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(repository, never()).findSaleableShowsByCinemaIds(any());
    }

    @Test
    void givenInvalidQueries_whenQuery_thenReturnInvalidParameter() {
        ShowQueryRepository repository = mock(ShowQueryRepository.class);
        SaleableShowBatchQueryService service = new SaleableShowBatchQueryService(repository, FIXED_CLOCK);
        List<Long> tooManyCinemas = new ArrayList<>();
        for (long cinemaId = 1; cinemaId <= 51; cinemaId++) {
            tooManyCinemas.add(cinemaId);
        }
        List<SaleableShowBatchQuery> invalidQueries = List.of(
                new SaleableShowBatchQuery(null, List.of(1L), null, null, 200),
                new SaleableShowBatchQuery(TODAY, List.of(0L), null, null, 200),
                new SaleableShowBatchQuery(TODAY, tooManyCinemas, null, null, 200),
                new SaleableShowBatchQuery(TODAY.minusDays(1), List.of(1L), null, null, 200),
                new SaleableShowBatchQuery(TODAY.plusDays(7), List.of(1L), null, null, 200),
                new SaleableShowBatchQuery(TODAY, List.of(1L), LocalTime.NOON, null, 200),
                new SaleableShowBatchQuery(TODAY, List.of(1L), LocalTime.NOON, LocalTime.NOON, 200),
                new SaleableShowBatchQuery(TODAY, List.of(1L), null, null, 0),
                new SaleableShowBatchQuery(TODAY, List.of(1L), null, null, 201));

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
                new SaleableShowBatchQuery(TODAY, List.of(21L), null, null, 200)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TicketingErrorCode.QUERY_UNAVAILABLE));
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
                3,
                startTime.minusHours(1));
    }
}
