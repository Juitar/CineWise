package com.miaoyu.ticket.job;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.RefundedTravelTaskReconciliationReport;
import com.miaoyu.ticket.order.application.RefundedTravelTaskReconciliationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每五分钟触发A的REFUNDED订单出行任务取消补偿。
 *
 * <p>Job只管理调度、trace和汇总日志；订单过滤、权威重读与跨模块边界均由
 * Application Service负责，调度层不得直接访问Mapper或D持久化层。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.transaction.refunded-travel-reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class RefundedTravelTaskReconciliationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundedTravelTaskReconciliationJob.class);

    private final RefundedTravelTaskReconciliationService reconciliationService;

    public RefundedTravelTaskReconciliationJob(
            RefundedTravelTaskReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** 每轮独立设置并清理traceId，避免调度线程复用污染下一项任务。 */
    @Scheduled(
            initialDelayString = "${cinewise.transaction.refunded-travel-reconciliation.delay-milliseconds:300000}",
            fixedDelayString = "${cinewise.transaction.refunded-travel-reconciliation.delay-milliseconds:300000}")
    public void reconcileRefundedTravelTasks() {
        MDC.put(TraceIdHolder.MDC_KEY, UUID.randomUUID().toString().replace("-", ""));
        try {
            RefundedTravelTaskReconciliationReport report = reconciliationService.reconcileRefundedOrders();
            if (report.scannedCount() > 0 || report.failedCount() > 0) {
                LOGGER.info(
                        "REFUNDED出行任务取消补偿完成, batches={}, scanned={}, ensured={}, skipped={}, failed={}",
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
