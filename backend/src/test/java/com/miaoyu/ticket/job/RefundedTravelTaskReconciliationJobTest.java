package com.miaoyu.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.RefundedTravelTaskReconciliationReport;
import com.miaoyu.ticket.order.application.RefundedTravelTaskReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class RefundedTravelTaskReconciliationJobTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void givenScheduledInvocation_whenServiceReturns_thenDelegateAndClearTrace() {
        RefundedTravelTaskReconciliationService service = mock(RefundedTravelTaskReconciliationService.class);
        when(service.reconcileRefundedOrders())
                .thenReturn(new RefundedTravelTaskReconciliationReport(1, 2, 1, 1, 0));
        RefundedTravelTaskReconciliationJob job = new RefundedTravelTaskReconciliationJob(service);

        job.reconcileRefundedTravelTasks();

        verify(service).reconcileRefundedOrders();
        assertThat(MDC.get(TraceIdHolder.MDC_KEY)).isNull();
    }

    @Test
    void givenServiceFailure_whenJobThrows_thenStillClearTrace() {
        RefundedTravelTaskReconciliationService service = mock(RefundedTravelTaskReconciliationService.class);
        when(service.reconcileRefundedOrders()).thenThrow(new IllegalStateException("测试扫描失败"));
        RefundedTravelTaskReconciliationJob job = new RefundedTravelTaskReconciliationJob(service);

        org.assertj.core.api.Assertions.assertThatThrownBy(job::reconcileRefundedTravelTasks)
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(TraceIdHolder.MDC_KEY)).isNull();
    }

    @Test
    void givenMissingEnablementProperty_whenEvaluatingJobCondition_thenDoNotRegisterJob() {
        ConditionalOnProperty condition = RefundedTravelTaskReconciliationJob.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("cinewise.transaction.refunded-travel-reconciliation");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
