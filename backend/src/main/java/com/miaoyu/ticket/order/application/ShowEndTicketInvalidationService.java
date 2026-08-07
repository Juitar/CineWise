package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.TicketingTransactionProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 有界扫描已结束场次的有效电子票，并委派独立事务完成条件失效。 */
@Service
public class ShowEndTicketInvalidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShowEndTicketInvalidationService.class);

    private final ElectronicTicketLifecycleRepository repository;
    private final ShowEndTicketInvalidationTransaction transaction;
    private final TicketingTransactionProperties properties;
    private final Clock clock;

    public ShowEndTicketInvalidationService(
            ElectronicTicketLifecycleRepository repository,
            ShowEndTicketInvalidationTransaction transaction,
            TicketingTransactionProperties properties,
            Clock clock) {
        this.repository = repository;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 每轮从数据库重新取得最早的有限候选，不保存内存游标。
     *
     * <p>服务重启或某条失败时，仍可在下一轮由数据库状态继续补扫。</p>
     */
    public ShowEndTicketInvalidationReport invalidateShowEndedTickets() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        List<ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate> candidates = repository
                .findShowEndedTicketCandidates(now, properties.showEndInvalidationBatchSize());
        int invalidatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate candidate : candidates) {
            try {
                if (transaction.invalidate(candidate, now)) {
                    invalidatedCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                // 仅记录内部票号与异常类型，避免任务日志泄露二维码或用户资料。
                LOGGER.warn(
                        "场次结束电子票失效失败, ticketId={}, errorType={}",
                        candidate.ticketId(),
                        exception.getClass().getSimpleName(),
                        exception);
            }
        }
        return new ShowEndTicketInvalidationReport(
                candidates.size(), invalidatedCount, skippedCount, failedCount);
    }
}
