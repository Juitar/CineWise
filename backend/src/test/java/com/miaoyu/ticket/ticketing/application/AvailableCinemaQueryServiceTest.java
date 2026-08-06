package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AvailableCinemaQueryServiceTest {

    @Test
    void givenSaleableCinema_whenQuery_thenMergeContentAndScheduleFacts() {
        AvailableCinemaQueryRepository repository = mock(AvailableCinemaQueryRepository.class);
        ContentSummaryQueryPort content = mock(ContentSummaryQueryPort.class);
        when(repository.findAvailableCinemas(any())).thenReturn(List.of(
                new AvailableCinemaQueryRepository.AvailableCinemaSnapshot(
                        20001L, 3, LocalDateTime.of(2026, 8, 8, 14, 0), "demo-seed",
                        LocalDateTime.of(2026, 8, 6, 10, 0))));
        ContentSummaryQueryPort.CinemaSummary summary = new ContentSummaryQueryPort.CinemaSummary(
                20001L, "妙语影城", "岳麓区", "岳麓大道 1 号", "LIVE", LocalDateTime.of(2026, 8, 6, 9, 0),
                LocalDateTime.of(2026, 8, 6, 10, 0), false);
        when(content.findCinemaSummaries(Set.of(20001L)))
                .thenReturn(new ContentSummaryQueryPort.CinemaSummaryBatch(List.of(summary), Set.of()));

        var result = service(repository, content).queryAvailableCinemas("10001", null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(view -> {
            assertThat(view.cinemaId()).isEqualTo(20001L);
            assertThat(view.availableShowCount()).isEqualTo(3);
            assertThat(view.name()).isEqualTo("妙语影城");
            assertThat(view.scheduleSource()).isEqualTo("demo-seed");
        });
    }

    @Test
    void givenInvalidMovieId_whenQuery_thenRejectBeforeRepository() {
        AvailableCinemaQueryRepository repository = mock(AvailableCinemaQueryRepository.class);

        assertThatThrownBy(() -> service(repository, mock(ContentSummaryQueryPort.class))
                .queryAvailableCinemas("01", null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        verify(repository, never()).findAvailableCinemas(any());
    }

    @Test
    void givenTicketingDatabaseUnavailable_whenQuery_thenReturn306003() {
        AvailableCinemaQueryRepository repository = mock(AvailableCinemaQueryRepository.class);
        when(repository.findAvailableCinemas(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> service(repository, mock(ContentSummaryQueryPort.class))
                .queryAvailableCinemas("10001", null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TicketingErrorCode.QUERY_UNAVAILABLE));
    }

    @Test
    void givenNoSaleableCinema_whenQuery_thenReturnEmptyWithoutContentLookup() {
        AvailableCinemaQueryRepository repository = mock(AvailableCinemaQueryRepository.class);
        ContentSummaryQueryPort content = mock(ContentSummaryQueryPort.class);
        when(repository.findAvailableCinemas(any())).thenReturn(List.of());

        var result = service(repository, content).queryAvailableCinemas("10001", 1, 20);

        assertThat(result.total()).isZero();
        assertThat(result.records()).isEmpty();
        verify(content, never()).findCinemaSummaries(any());
    }

    @Test
    void givenContentUnavailable_whenQuery_thenKeep303004ForCaller() {
        AvailableCinemaQueryRepository repository = mock(AvailableCinemaQueryRepository.class);
        ContentSummaryQueryPort content = mock(ContentSummaryQueryPort.class);
        when(repository.findAvailableCinemas(any())).thenReturn(List.of(
                new AvailableCinemaQueryRepository.AvailableCinemaSnapshot(
                        20001L, 1, LocalDateTime.of(2026, 8, 8, 14, 0), "demo-seed",
                        LocalDateTime.of(2026, 8, 6, 10, 0))));
        when(content.findCinemaSummaries(any()))
                .thenThrow(new BusinessException(ContentSummaryQueryPort.ContentSummaryErrorCode.DATA_UNAVAILABLE));

        assertThatThrownBy(() -> service(repository, content).queryAvailableCinemas("10001", 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ContentSummaryQueryPort.ContentSummaryErrorCode.DATA_UNAVAILABLE));
    }

    private AvailableCinemaQueryService service(
            AvailableCinemaQueryRepository repository, ContentSummaryQueryPort content) {
        return new AvailableCinemaQueryService(repository, content, new ApiProperties(1, 20, 50),
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }
}
