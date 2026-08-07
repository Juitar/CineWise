package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.config.RefundedTravelReconciliationProperties;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.event.OrderInvalidated;
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

class RefundedTravelTaskReconciliationServiceTest {

    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2026, 8, 5, 8, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void givenMultipleBatches_whenReconcile_thenAdvanceCursorAndCancelEveryTask() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelEventContextResolver contextResolver = mock(TravelEventContextResolver.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        RefundedTravelTaskReconciliationService service = new RefundedTravelTaskReconciliationService(
                repository,
                contextResolver,
                travelService,
                properties(2),
                FIXED_CLOCK);
        OrderRepository.RefundedTravelReconciliationCandidate first =
                candidate(301L, WINDOW_END.minusHours(3), 4);
        OrderRepository.RefundedTravelReconciliationCandidate second =
                candidate(302L, WINDOW_END.minusHours(2), 4);
        OrderRepository.RefundedTravelReconciliationCandidate third =
                candidate(303L, WINDOW_END.minusHours(1), 4);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findRefundedTravelReconciliationCandidates(
                windowStart, WINDOW_END, windowStart, 0L, 2))
                .thenReturn(List.of(first, second));
        when(repository.findRefundedTravelReconciliationCandidates(
                windowStart, WINDOW_END, second.refundedAt(), second.orderId(), 2))
                .thenReturn(List.of(third));
        for (OrderRepository.RefundedTravelReconciliationCandidate candidate : List.of(first, second, third)) {
            OrderRepository.OrderSnapshot order = refundedOrder(candidate);
            when(repository.findById(candidate.orderId())).thenReturn(Optional.of(order));
            when(contextResolver.resolve(order)).thenReturn(Optional.of(context(candidate.showId())));
        }

        RefundedTravelTaskReconciliationReport report = service.reconcileRefundedOrders();

        assertThat(report).isEqualTo(new RefundedTravelTaskReconciliationReport(2, 3, 3, 0, 0));
        ArgumentCaptor<OrderInvalidated> events = ArgumentCaptor.forClass(OrderInvalidated.class);
        verify(travelService, org.mockito.Mockito.times(3)).ensureTaskCancelled(events.capture());
        assertThat(events.getAllValues()).extracting(OrderInvalidated::orderId)
                .containsExactly("301", "302", "303");
        assertThat(events.getAllValues()).extracting(OrderInvalidated::invalidReason)
                .containsOnly("REFUNDED");
        assertThat(events.getAllValues()).extracting(OrderInvalidated::cinemaId)
                .containsExactly("30301", "30302", "30303")
                .allMatch(cinemaId -> cinemaId.matches("[1-9][0-9]*"));
        assertThat(events.getAllValues()).extracting(OrderInvalidated::occurredAt)
                .extracting(java.time.OffsetDateTime::toLocalDateTime)
                .containsExactly(first.refundedAt(), second.refundedAt(), third.refundedAt());
    }

    @Test
    void givenStateChangesMissingContextAndProviderFailure_whenReconcile_thenIsolateCandidates() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelEventContextResolver contextResolver = mock(TravelEventContextResolver.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        RefundedTravelTaskReconciliationService service = new RefundedTravelTaskReconciliationService(
                repository,
                contextResolver,
                travelService,
                properties(4),
                FIXED_CLOCK);
        OrderRepository.RefundedTravelReconciliationCandidate paid =
                candidate(401L, WINDOW_END.minusHours(4), 4);
        OrderRepository.RefundedTravelReconciliationCandidate changedVersion =
                candidate(402L, WINDOW_END.minusHours(3), 4);
        OrderRepository.RefundedTravelReconciliationCandidate missingContext =
                candidate(403L, WINDOW_END.minusHours(2), 4);
        OrderRepository.RefundedTravelReconciliationCandidate providerFailure =
                candidate(404L, WINDOW_END.minusHours(1), 4);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findRefundedTravelReconciliationCandidates(
                windowStart, WINDOW_END, windowStart, 0L, 4))
                .thenReturn(List.of(paid, changedVersion, missingContext, providerFailure));
        when(repository.findRefundedTravelReconciliationCandidates(
                windowStart, WINDOW_END, providerFailure.refundedAt(), providerFailure.orderId(), 4))
                .thenReturn(List.of());
        when(repository.findById(paid.orderId()))
                .thenReturn(Optional.of(order(paid, OrderStatus.PAID, paid.orderVersion() - 2)));
        when(repository.findById(changedVersion.orderId()))
                .thenReturn(Optional.of(order(
                        changedVersion,
                        OrderStatus.REFUNDED,
                        changedVersion.orderVersion() + 1)));
        OrderRepository.OrderSnapshot missingContextOrder = refundedOrder(missingContext);
        when(repository.findById(missingContext.orderId())).thenReturn(Optional.of(missingContextOrder));
        when(contextResolver.resolve(missingContextOrder)).thenReturn(Optional.empty());
        OrderRepository.OrderSnapshot providerFailureOrder = refundedOrder(providerFailure);
        when(repository.findById(providerFailure.orderId())).thenReturn(Optional.of(providerFailureOrder));
        when(contextResolver.resolve(providerFailureOrder))
                .thenReturn(Optional.of(context(providerFailure.showId())));
        when(travelService.ensureTaskCancelled(any()))
                .thenThrow(new IllegalStateException("测试D取消入口失败"));

        RefundedTravelTaskReconciliationReport report = service.reconcileRefundedOrders();

        assertThat(report).isEqualTo(new RefundedTravelTaskReconciliationReport(1, 4, 0, 3, 1));
        verify(travelService).ensureTaskCancelled(any());
        verifyNoMoreInteractions(travelService);
    }

    @Test
    void givenNoRefundedCandidate_whenReconcile_thenReturnEmptyReportWithoutCallingTravel() {
        OrderRepository repository = mock(OrderRepository.class);
        TravelTaskApplicationService travelService = mock(TravelTaskApplicationService.class);
        LocalDateTime windowStart = WINDOW_END.minusHours(24);
        when(repository.findRefundedTravelReconciliationCandidates(
                windowStart, WINDOW_END, windowStart, 0L, 100))
                .thenReturn(List.of());
        RefundedTravelTaskReconciliationService service = new RefundedTravelTaskReconciliationService(
                repository,
                mock(TravelEventContextResolver.class),
                travelService,
                properties(100),
                FIXED_CLOCK);

        assertThat(service.reconcileRefundedOrders())
                .isEqualTo(new RefundedTravelTaskReconciliationReport(0, 0, 0, 0, 0));
        verifyNoMoreInteractions(travelService);
    }

    private RefundedTravelReconciliationProperties properties(int batchSize) {
        return new RefundedTravelReconciliationProperties(true, 300_000, 24, batchSize);
    }

    private OrderRepository.RefundedTravelReconciliationCandidate candidate(
            long orderId,
            LocalDateTime refundedAt,
            int version) {
        return new OrderRepository.RefundedTravelReconciliationCandidate(
                orderId,
                orderId + 10_000,
                orderId + 20_000,
                version,
                refundedAt);
    }

    private OrderRepository.OrderSnapshot refundedOrder(
            OrderRepository.RefundedTravelReconciliationCandidate candidate) {
        return order(candidate, OrderStatus.REFUNDED, candidate.orderVersion());
    }

    private OrderRepository.OrderSnapshot order(
            OrderRepository.RefundedTravelReconciliationCandidate candidate,
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
