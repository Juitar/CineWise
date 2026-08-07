package com.miaoyu.ticket.order.infrastructure.persistence;

import com.miaoyu.ticket.order.application.ElectronicTicketLifecycleRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 将持久层行投影转换为应用层电子票生命周期端口。 */
@Repository
public class MybatisElectronicTicketLifecycleRepository implements ElectronicTicketLifecycleRepository {

    private final ElectronicTicketLifecycleMapper mapper;

    public MybatisElectronicTicketLifecycleRepository(ElectronicTicketLifecycleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ShowEndedTicketCandidate> findShowEndedTicketCandidates(
            LocalDateTime endedAtOrBefore,
            int limit) {
        return mapper.findShowEndedTicketCandidates(endedAtOrBefore, limit).stream()
                .map(row -> new ShowEndedTicketCandidate(row.ticketId(), row.ticketVersion()))
                .toList();
    }

    @Override
    public boolean invalidateAfterShowEnd(
            long ticketId,
            int expectedVersion,
            LocalDateTime endedAtOrBefore,
            LocalDateTime invalidatedAt) {
        return mapper.invalidateAfterShowEnd(
                ticketId,
                expectedVersion,
                endedAtOrBefore,
                invalidatedAt) == 1;
    }
}
