package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.ShowContextQueryService;
import com.miaoyu.ticket.ticketing.application.ShowQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证出行事件只能使用与订单一致的A权威场次和正影院业务ID。 */
class TravelEventContextResolverTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 8, 6, 10, 0);

    @Test
    void givenMismatchedOrNonPositiveCinemaContext_whenResolve_thenReturnEmptyWithoutFabricatingEventFacts() {
        ShowQueryRepository showRepository = mock(ShowQueryRepository.class);
        ContentSummaryQueryPort contentSummaryQueryPort = mock(ContentSummaryQueryPort.class);
        TravelEventContextResolver resolver = new TravelEventContextResolver(
                new ShowContextQueryService(showRepository), contentSummaryQueryPort);
        OrderRepository.OrderSnapshot order = order(70001L);

        when(showRepository.findShowContext(order.showId())).thenReturn(Optional.of(
                new ShowQueryRepository.ShowContext(70002L, 0L, FIXED_TIME)));

        assertThat(resolver.resolve(order)).isEmpty();
        // 无效的A场次关联不能向D摘要端口查询，更不能用默认影院ID拼接事件。
        verifyNoInteractions(contentSummaryQueryPort);
    }

    private OrderRepository.OrderSnapshot order(long showId) {
        return new OrderRepository.OrderSnapshot(
                90001L,
                "CW90001",
                80001L,
                showId,
                1,
                new BigDecimal("39.00"),
                new BigDecimal("39.00"),
                OrderStatus.PAID,
                FIXED_TIME.plusHours(1),
                "request-1",
                "key-1",
                3,
                FIXED_TIME);
    }
}
