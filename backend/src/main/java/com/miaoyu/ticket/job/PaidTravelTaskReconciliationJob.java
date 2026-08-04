package com.miaoyu.ticket.job;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.PaidTravelTaskReconciliationReport;
import com.miaoyu.ticket.order.application.PaidTravelTaskReconciliationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 显式启用后每五分钟触发A的PAID订单出行任务补偿应用服务。
 *
 * <p>Job只负责调度、trace和汇总日志，不直接读取订单Mapper或D的任务持久层。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.transaction.paid-travel-reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PaidTravelTaskReconciliationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaidTravelTaskReconciliationJob.class);

    private final PaidTravelTaskReconciliationService reconciliationService;

    /**
     * Job只依赖A的应用服务，确保调度层不会绕过分页、状态重读或单条失败隔离规则。
     *
     * @param reconciliationService A拥有的PAID出行任务补偿用例
     */
    public PaidTravelTaskReconciliationJob(PaidTravelTaskReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 每轮使用独立traceId，并在结束时清理MDC，避免调度线程复用污染后续任务。
     * 服务级异常会交给统一调度ErrorHandler记录，单条候选异常则已在应用服务内隔离。
     */
    @Scheduled(
            initialDelayString = "${cinewise.transaction.paid-travel-reconciliation.delay-milliseconds:300000}",
            fixedDelayString = "${cinewise.transaction.paid-travel-reconciliation.delay-milliseconds:300000}")
    public void reconcilePaidTravelTasks() {
        MDC.put(TraceIdHolder.MDC_KEY, UUID.randomUUID().toString().replace("-", ""));
        try {
            PaidTravelTaskReconciliationReport report = reconciliationService.reconcilePaidOrders();
            if (report.scannedCount() > 0 || report.failedCount() > 0) {
                LOGGER.info(
                        "PAID出行任务补偿完成, batches={}, scanned={}, ensured={}, skipped={}, failed={}",
                        report.batchCount(),
                        report.scannedCount(),
                        report.ensuredCount(),
                        report.skippedCount(),
                        report.failedCount());
            }
        } finally {
            MDC.remove(TraceIdHolder.MDC_KEY);
        }
    }
}
