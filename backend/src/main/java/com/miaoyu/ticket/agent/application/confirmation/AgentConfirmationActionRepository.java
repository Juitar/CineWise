package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import java.util.Optional;

/** B 自有确认动作的持久化端口；生产实现必须使用版本 CAS。 */
public interface AgentConfirmationActionRepository {
    Optional<AgentConfirmationAction> findByActionId(String actionId);

    /**
     * CAS 失败后使用当前读取得提交胜者，避免事务内的普通快照读到旧状态并再次执行写工具。
     */
    Optional<AgentConfirmationAction> findByActionIdForUpdate(String actionId);

    Optional<AgentConfirmationAction> findByCreationKey(
            long userId,
            long agentRunId,
            String planId,
            int planVersion,
            String nodeId,
            String toolName,
            String parameterHash);

    /** 唯一键冲突后使用当前读取得提交的创建胜者，避免可重复读快照读不到它。 */
    Optional<AgentConfirmationAction> findByCreationKeyForUpdate(
            long userId,
            long agentRunId,
            String planId,
            int planVersion,
            String nodeId,
            String toolName,
            String parameterHash);

    void insert(AgentConfirmationAction action);

    boolean compareAndSet(
            String actionId,
            long expectedVersion,
            AgentConfirmationActionStatus expectedStatus,
            AgentConfirmationAction next);
}
