package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import java.util.Optional;

/** B 自有确认动作的持久化端口；生产实现必须使用版本 CAS。 */
public interface AgentConfirmationActionRepository {
    Optional<AgentConfirmationAction> findByActionId(String actionId);

    void insert(AgentConfirmationAction action);

    boolean compareAndSet(
            String actionId,
            long expectedVersion,
            AgentConfirmationActionStatus expectedStatus,
            AgentConfirmationAction next);
}
