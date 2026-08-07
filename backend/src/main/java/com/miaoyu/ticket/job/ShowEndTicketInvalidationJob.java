package com.miaoyu.ticket.job;

import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.order.application.ShowEndTicketInvalidationReport;
import com.miaoyu.ticket.order.application.ShowEndTicketInvalidationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时调用电子票结束失效应用服务，不访问票务Mapper或其他领域状态。 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.transaction",
        name = "show-end-invalidation-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ShowEndTicketInvalidationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShowEndTicketInvalidationJob.class);

    private final ShowEndTicketInvalidationService invalidationService;

    public ShowEndTicketInvalidationJob(ShowEndTicketInvalidationService invalidationService) {
        this.invalidationService = invalidationService;
    }

    /** 为每轮任务隔离traceId，并在调度线程归还前清理MDC。 */
    @Scheduled(fixedDelayString = "${cinewise.transaction.show-end-invalidation-job-delay-milliseconds:60000}")
    public void invalidateShowEndedTickets() {
        MDC.put(TraceIdHolder.MDC_KEY, UUID.randomUUID().toString().replace("-", ""));
        try {
            ShowEndTicketInvalidationReport report = invalidationService.invalidateShowEndedTickets();
            if (report.scannedCount() > 0 || report.failedCount() > 0) {
                LOGGER.info(
                        "场次结束电子票批处理完成, scanned={}, invalidated={}, skipped={}, failed={}",
                        report.scannedCount(),
                        report.invalidatedCount(),
                        report.skippedCount(),
                        report.failedCount());
            }
        } finally {
            MDC.remove(TraceIdHolder.MDC_KEY);
        }
    }
}
