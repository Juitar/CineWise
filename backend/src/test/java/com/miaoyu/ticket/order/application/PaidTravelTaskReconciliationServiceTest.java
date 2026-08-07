package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.config.PaidTravelReconciliationProperties;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.application.TravelTaskApplicationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaidTravelTaskReconciliationServiceTest {

    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2026, 8, 5, 8, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void givenMoreCandidatesThanBatch_whenReconcile_thenUseStableCursorAndEnsureEveryTask() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelEventContextResolver contextResolver = mock(TravelEventContextResolver.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        PaidTravelReconciliationProperties properties = properties(2);
        PaidTravelTaskReconciliationService service = new PaidTravelTaskReconciliationService(
                repository,
                contextResolver,
                travelService,
                properties,
                FIXED_CLOCK);
        OrderRepository.PaidTravelReconciliationCandidate first = candidate(101L, WINDOW_END.minusHours(3), 2);
        OrderRepository.PaidTravelReconciliationCandidate second = candidate(102L, WINDOW_END.minusHours(2), 2);
        OrderRepository.PaidTravelReconciliationCandidate third = candidate(103L, WINDOW_END.minusHours(1), 2);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findPaidTravelReconciliationCandidates(windowStart, WINDOW_END, windowStart, 0L, 2))
                .thenReturn(List.of(first, second));
        when(repository.findPaidTravelReconciliationCandidates(
                windowStart,
                WINDOW_END,
                second.paidAt(),
                second.orderId(),
                2))
                .thenReturn(List.of(third));
        for (OrderRepository.PaidTravelReconciliationCandidate candidate : List.of(first, second, third)) {
            OrderRepository.OrderSnapshot order = paidOrder(candidate);
            when(repository.findById(candidate.orderId())).thenReturn(Optional.of(order));
            when(contextResolver.resolve(order)).thenReturn(Optional.of(context(candidate.showId())));
        }

        PaidTravelTaskReconciliationReport report = service.reconcilePaidOrders();

        assertThat(report).isEqualTo(new PaidTravelTaskReconciliationReport(2, 3, 3, 0, 0));
        ArgumentCaptor<PaymentSucceededEvent> events = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(travelService, org.mockito.Mockito.times(3)).ensureTask(events.capture());
        assertThat(events.getAllValues()).extracting(PaymentSucceededEvent::orderId)
                .containsExactly("101", "102", "103");
        assertThat(events.getAllValues()).extracting(PaymentSucceededEvent::cinemaId)
                .containsExactly("30101", "30102", "30103")
                .allMatch(cinemaId -> cinemaId.matches("[1-9][0-9]*"));
        assertThat(events.getAllValues()).extracting(PaymentSucceededEvent::occurredAt)
                .extracting(java.time.OffsetDateTime::toLocalDateTime)
                .containsExactly(first.paidAt(), second.paidAt(), third.paidAt());
    }

    @Test
    void givenStateChangesMissingContextAndProviderFailure_whenReconcile_thenSkipOrFailPerOrder() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelEventContextResolver contextResolver = mock(TravelEventContextResolver.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        PaidTravelTaskReconciliationService service = new PaidTravelTaskReconciliationService(
                repository,
                contextResolver,
                travelService,
                properties(4),
                FIXED_CLOCK);
        OrderRepository.PaidTravelReconciliationCandidate refunded = candidate(201L, WINDOW_END.minusHours(4), 2);
        OrderRepository.PaidTravelReconciliationCandidate changedVersion =
                candidate(202L, WINDOW_END.minusHours(3), 2);
        OrderRepository.PaidTravelReconciliationCandidate missingContext =
                candidate(203L, WINDOW_END.minusHours(2), 2);
        OrderRepository.PaidTravelReconciliationCandidate providerFailure =
                candidate(204L, WINDOW_END.minusHours(1), 2);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findPaidTravelReconciliationCandidates(windowStart, WINDOW_END, windowStart, 0L, 4))
                .thenReturn(List.of(refunded, changedVersion, missingContext, providerFailure));
        when(repository.findPaidTravelReconciliationCandidates(
                windowStart,
                WINDOW_END,
                providerFailure.paidAt(),
                providerFailure.orderId(),
                4))
                .thenReturn(List.of());
        when(repository.findById(refunded.orderId()))
                .thenReturn(Optional.of(order(refunded, OrderStatus.REFUNDED, refunded.orderVersion() + 2)));
        when(repository.findById(changedVersion.orderId()))
                .thenReturn(Optional.of(order(changedVersion, OrderStatus.PAID, changedVersion.orderVersion() + 1)));
        OrderRepository.OrderSnapshot missingContextOrder = paidOrder(missingContext);
        when(repository.findById(missingContext.orderId())).thenReturn(Optional.of(missingContextOrder));
        when(contextResolver.resolve(missingContextOrder)).thenReturn(Optional.empty());
        OrderRepository.OrderSnapshot providerFailureOrder = paidOrder(providerFailure);
        when(repository.findById(providerFailure.orderId())).thenReturn(Optional.of(providerFailureOrder));
        when(contextResolver.resolve(providerFailureOrder))
                .thenReturn(Optional.of(context(providerFailure.showId())));
        when(travelService.ensureTask(any())).thenThrow(new IllegalStateException("测试D入口失败"));

        PaidTravelTaskReconciliationReport report = service.reconcilePaidOrders();

        assertThat(report).isEqualTo(new PaidTravelTaskReconciliationReport(1, 4, 0, 3, 1));
        verify(travelService).ensureTask(any());
        verifyNoMoreInteractions(travelService);
    }

    @Test
    void givenNoPaidCandidate_whenReconcile_thenReturnEmptyReportWithoutCallingTravel() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findPaidTravelReconciliationCandidates(windowStart, WINDOW_END, windowStart, 0L, 100))
                .thenReturn(List.of());
        PaidTravelTaskReconciliationService service = new PaidTravelTaskReconciliationService(
                repository,
                mock(TravelEventContextResolver.class),
                travelService,
                properties(100),
                FIXED_CLOCK);

        assertThat(service.reconcilePaidOrders())
                .isEqualTo(new PaidTravelTaskReconciliationReport(0, 0, 0, 0, 0));
        verifyNoMoreInteractions(travelService);
    }

    private PaidTravelReconciliationProperties properties(int batchSize) {
        return new PaidTravelReconciliationProperties(true, 300_000, 24, batchSize);
    }

    private OrderRepository.PaidTravelReconciliationCandidate candidate(
            long orderId,
            LocalDateTime paidAt,
            int version) {
        return new OrderRepository.PaidTravelReconciliationCandidate(
                orderId,
                orderId + 10_000,
                orderId + 20_000,
                version,
                paidAt);
    }

    private OrderRepository.OrderSnapshot paidOrder(
            OrderRepository.PaidTravelReconciliationCandidate candidate) {
        return order(candidate, OrderStatus.PAID, candidate.orderVersion());
    }

    private OrderRepository.OrderSnapshot order(
            OrderRepository.PaidTravelReconciliationCandidate candidate,
            OrderStatus status,
            int version) {
        return new OrderRepository.OrderSnapshot(
                candidate.orderId(),
                "ORD" + candidate.orderId(),
                candidate.userId(),
                candidate.showId(),
                1,
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                status,
                WINDOW_END.plusHours(1),
                "request-" + candidate.orderId(),
                "key-" + candidate.orderId(),
                version,
                WINDOW_END);
    }

    private TravelEventContextResolver.TravelEventContext context(long showId) {
        return new TravelEventContextResolver.TravelEventContext(
                showId,
                showId + 5_000L,
                showId + 10_000L,
                "西湖区",
                WINDOW_END.plusHours(6));
    }
}
