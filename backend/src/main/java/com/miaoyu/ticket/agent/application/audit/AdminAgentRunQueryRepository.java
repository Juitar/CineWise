package com.miaoyu.ticket.agent.application.audit;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Agent 模块自己的管理员只读查询端口。 */
public interface AdminAgentRunQueryRepository {

    long count(Criteria criteria);

    List<AgentRun> findPage(Criteria criteria);

    Optional<AgentRun> findByRunId(String runId);

    record Criteria(
            AgentRunStatus status,
            Set<Long> userIds,
            LocalDateTime startedFrom,
            LocalDateTime startedTo,
            int offset,
            int size) {
    }
}
