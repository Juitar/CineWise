package com.miaoyu.ticket.job;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.OrderExpiryReport;
import com.miaoyu.ticket.order.application.OrderExpiryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每30秒触发A订单过期应用服务，不直接访问任何Mapper。 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.transaction",
        name = "expiry-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExpiredOrderReleaseJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredOrderReleaseJob.class);

    private final OrderExpiryService orderExpiryService;

    public ExpiredOrderReleaseJob(OrderExpiryService orderExpiryService) {
        this.orderExpiryService = orderExpiryService;
    }

    /** 为每轮调度创建独立traceId，任务结束时清理MDC防止线程复用污染。 */
    @Scheduled(fixedDelayString = "${cinewise.transaction.expiry-job-delay-milliseconds:30000}")
    public void releaseExpiredOrders() {
        MDC.put(TraceIdHolder.MDC_KEY, UUID.randomUUID().toString().replace("-", ""));
        try {
            OrderExpiryReport report = orderExpiryService.releaseExpiredOrders();
            if (report.scannedCount() > 0 || report.failedCount() > 0) {
                LOGGER.info(
                        "过期订单批处理完成, scanned={}, expired={}, skipped={}, failed={}",
                        report.scannedCount(),
                        report.expiredCount(),
                        report.skippedCount(),
                        report.failedCount());
            }
        } finally {
            MDC.remove(TraceIdHolder.MDC_KEY);
        }
    }
}
