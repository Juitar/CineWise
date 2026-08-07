package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** B 自有运行存储端口；客户端请求幂等查询必须同时限制用户和会话。 */
public interface AgentRunRepository {

    Optional<AgentRun> findByRunIdAndUserId(String runId, long userId);

    Optional<AgentRun> findByClientRequestId(long userId, long sessionId, String clientRequestId);

    /** 历史消息只批量读取已验证用户和会话内的运行，避免暴露消息中的内部关联 ID。 */
    List<AgentRun> findByIdsAndUserIdAndSessionId(List<Long> runIds, long userId, long sessionId);

    List<AgentRun> findStaleRunningBefore(LocalDateTime cutoff, int limit);

    /** 使用数据库当前时间挑选已超过等待期限的本人运行，避免应用节点时钟不一致。 */
    List<AgentRun> findExpiredWaitingLocationByUser(long userId, int limit);

    void insert(AgentRun run);

    /** 初始化等待运行时由数据库写入等待起点，避免不同应用节点的 Clock 影响五分钟期限。 */
    void insertWaitingLocation(AgentRun run);

    /** 保存合法计划但保持运行中状态；影响行数为零说明运行已被其他事务终结。 */
    boolean updateRunningPlanWithCas(AgentRun run, long expectedVersion);

    /** 按运行版本和 RUNNING 前置状态完成一次恢复或终态落库。 */
    boolean updateTerminalWithCas(AgentRun run, long expectedVersion);

    /** 等待位置等非终态转换必须同时限定旧状态与版本，不能覆盖取消或已完成运行。 */
    boolean updateWithCas(AgentRun run, long expectedVersion, AgentRunStatus expectedStatus);

    /** 超时恢复同时校验 WAITING_LOCATION、版本及数据库侧五分钟期限。 */
    boolean recoverExpiredWaitingLocationWithCas(AgentRun run, long expectedVersion);

    boolean deleteTerminalExpiredById(long runId, LocalDateTime now);

    boolean hasRuns(long sessionId);
}
