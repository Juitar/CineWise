package com.miaoyu.ticket.order.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单张电子票的独立事务，失败不会回滚同批其他票据。 */
@Service
public class ShowEndTicketInvalidationTransaction {

    private final ElectronicTicketLifecycleRepository repository;

    public ShowEndTicketInvalidationTransaction(ElectronicTicketLifecycleRepository repository) {
        this.repository = repository;
    }

    /** 条件更新未命中表示退款、其他任务或并发调用已经取得了权威状态。 */
    @Transactional
    public boolean invalidate(ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate candidate,
            LocalDateTime now) {
        return repository.invalidateAfterShowEnd(
                candidate.ticketId(),
                candidate.ticketVersion(),
                now,
                now);
    }
}
