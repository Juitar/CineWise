package com.miaoyu.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.PaidTravelTaskReconciliationReport;
import com.miaoyu.ticket.order.application.PaidTravelTaskReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class PaidTravelTaskReconciliationJobTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void givenScheduledInvocation_whenServiceReturns_thenDelegateAndClearTrace() {
        PaidTravelTaskReconciliationService service = mock(PaidTravelTaskReconciliationService.class);
        when(service.reconcilePaidOrders())
                .thenReturn(new PaidTravelTaskReconciliationReport(1, 2, 1, 1, 0));
        PaidTravelTaskReconciliationJob job = new PaidTravelTaskReconciliationJob(service);

        job.reconcilePaidTravelTasks();

        verify(service).reconcilePaidOrders();
        assertThat(MDC.get(TraceIdHolder.MDC_KEY)).isNull();
    }

    @Test
    void givenServiceFailure_whenJobThrows_thenStillClearTrace() {
        PaidTravelTaskReconciliationService service = mock(PaidTravelTaskReconciliationService.class);
        when(service.reconcilePaidOrders()).thenThrow(new IllegalStateException("测试扫描失败"));
        PaidTravelTaskReconciliationJob job = new PaidTravelTaskReconciliationJob(service);

        org.assertj.core.api.Assertions.assertThatThrownBy(job::reconcilePaidTravelTasks)
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(TraceIdHolder.MDC_KEY)).isNull();
    }

    @Test
    void givenMissingEnablementProperty_whenEvaluatingJobCondition_thenDoNotRegisterJob() {
        ConditionalOnProperty condition = PaidTravelTaskReconciliationJob.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("cinewise.transaction.paid-travel-reconciliation");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
