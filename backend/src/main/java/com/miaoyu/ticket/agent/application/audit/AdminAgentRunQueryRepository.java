package com.miaoyu.ticket.agent.application.audit;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Agent 模块自己的管理员只读查询端口。 */
public interface AdminAgentRunQueryRepository {

    long count(Criteria criteria);

    List<AgentRun> findPage(Criteria criteria);

    /** 批量读取当前页运行的步骤计数，不读取任何步骤 JSON 或事件内容。 */
    Map<Long, NodeStats> findNodeStatsByRunIds(List<Long> runIds);

    Optional<AgentRun> findByRunId(String runId);

    record Criteria(
            AgentRunStatus status,
            Set<Long> userIds,
            LocalDateTime startedFrom,
            LocalDateTime startedTo,
            int offset,
            int size) {
    }

    record NodeStats(int nodeCount, int completedNodeCount, int failedNodeCount) {
        public static final NodeStats EMPTY = new NodeStats(0, 0, 0);
    }
}
