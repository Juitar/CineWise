package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** B 自有运行存储端口；客户端请求幂等查询必须同时限制用户和会话。 */
public interface AgentRunRepository {

    Optional<AgentRun> findByRunIdAndUserId(String runId, long userId);

    Optional<AgentRun> findByClientRequestId(long userId, long sessionId, String clientRequestId);

    List<AgentRun> findStaleRunningBefore(LocalDateTime cutoff, int limit);

    void insert(AgentRun run);

    /** 保存合法计划但保持运行中状态；影响行数为零说明运行已被其他事务终结。 */
    boolean updateRunningPlanWithCas(AgentRun run, long expectedVersion);

    /** 按运行版本和 RUNNING 前置状态完成一次恢复或终态落库。 */
    boolean updateTerminalWithCas(AgentRun run, long expectedVersion);
}
