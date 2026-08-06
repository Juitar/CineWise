package com.miaoyu.ticket.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.ticketing.application.SaleableShowBatchQueryService;
import com.miaoyu.ticket.ticketing.application.SaleableShowBatchResult;
import com.miaoyu.ticket.ticketing.application.SaleableShowView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TicketingBatchShowtimeQueryAdapterTest {
    @Test
    void preservesAFieldsAndTruncation() {
        SaleableShowBatchQueryService service = Mockito.mock(SaleableShowBatchQueryService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 10, 0);
        SaleableShowView show = new SaleableShowView(3L, 1L, 2L, new BigDecimal("39.90"), now.plusHours(2),
                now.plusHours(4), "MOCK", "seed", now, now.plusMinutes(1), true, 20, 1, now);
        when(service.query(Mockito.any())).thenReturn(new SaleableShowBatchResult(List.of(show), true));
        var result = new TicketingBatchShowtimeQueryAdapter(service).querySaleable(
                LocalDate.of(2026, 8, 6), Set.of(2L));
        assertThat(result.truncated()).isTrue();
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.showId()).isEqualTo("3");
            assertThat(candidate.price()).isEqualByComparingTo("39.90");
            // 既保留 MOCK 大类，也保留 A 给出的 demo-seed 具体来源。
            assertThat(candidate.source()).isEqualTo("TICKETING:MOCK:seed");
        });
    }
}
