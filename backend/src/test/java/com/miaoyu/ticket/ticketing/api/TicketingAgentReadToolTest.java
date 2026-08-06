package com.miaoyu.ticket.ticketing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.ticketing.application.AvailableDateQueryService;
import com.miaoyu.ticket.ticketing.application.AvailableDateView;
import com.miaoyu.ticket.ticketing.application.ShowQueryService;
import com.miaoyu.ticket.ticketing.application.ShowSummaryView;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A 票务 Tool API 只验证自身结果映射和已确认错误语义，不启动 Agent 编排器。 */
class TicketingAgentReadToolTest {
    private static final Instant DATA_AT = Instant.parse("2026-08-08T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(DATA_AT, ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldReturnEmptyDatesAsSuccessfulReadResult() {
        AvailableDateQueryService service = mock(AvailableDateQueryService.class);
        when(service.queryAvailableDates(101L, 201L)).thenReturn(List.of());

        var result = new QueryAvailableDatesTool(service, CLOCK)
                .execute(
                        context(QueryAvailableDatesTool.TARGET_NAME),
                        new QueryAvailableDatesToolCommand("101", "201"));

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().dates()).isEmpty();
        assertThat(result.suggestedNextAction()).isEqualTo("CHOOSE_MOVIE_OR_CINEMA");
        assertThat(result.dataAt()).isEqualTo(DATA_AT);
        assertThat(result.expiresAt()).isEqualTo(DATA_AT.plusSeconds(5L));
    }

    @Test
    void shouldMapAvailableDateQueryUnavailableToRetryableFailure() {
        AvailableDateQueryService service = mock(AvailableDateQueryService.class);
        when(service.queryAvailableDates(anyLong(), anyLong()))
                .thenThrow(new BusinessException(TicketingErrorCode.QUERY_UNAVAILABLE));

        var result = new QueryAvailableDatesTool(service, CLOCK)
                .execute(
                        context(QueryAvailableDatesTool.TARGET_NAME),
                        new QueryAvailableDatesToolCommand("101", "201"));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(306003);
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void shouldMapShowSummaryToAgentResultWithoutSeatDetails() {
        ShowQueryService service = mock(ShowQueryService.class);
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 19, 30);
        when(service.queryShows(any())).thenReturn(List.of(new ShowSummaryView(
                301L, 101L, 201L, "万达影城", 401L, "IMAX厅", start, start.plusMinutes(130), start,
                "国语3D", new BigDecimal("45.00"), 32, "ON_SALE", "REAL", 7, start.minusMinutes(5))));

        var result = new QueryShowsTool(service, CLOCK).execute(
                context(QueryShowsTool.TARGET_NAME),
                new QueryShowsToolCommand("101", "201", LocalDate.of(2026, 8, 8), null, null));

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.dataAt()).isEqualTo(DATA_AT);
        assertThat(result.expiresAt()).isEqualTo(DATA_AT.plusSeconds(5L));
        assertThat(result.data().shows()).singleElement().satisfies(show -> {
            assertThat(show.showId()).isEqualTo("301");
            assertThat(show.basePrice()).isEqualTo("45.00");
            assertThat(show.availableSeatCount()).isEqualTo(32);
            assertThat(show.expiresAt()).isEqualTo(
                    start.atZone(com.miaoyu.ticket.common.config.ClockConfiguration.BUSINESS_ZONE_ID)
                            .toOffsetDateTime());
        });
    }

    private static ToolContext context(String targetName) {
        return new ToolContext("run-1", "node-1", targetName, List.of("slots.movieId"),
                5_000L, "trace-1", null, null, 1L);
    }
}
