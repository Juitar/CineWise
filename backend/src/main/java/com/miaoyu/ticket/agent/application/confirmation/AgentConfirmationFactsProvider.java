package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;

/** 确认前重新读取运行、计划、节点和业务候选的端口。 */
public interface AgentConfirmationFactsProvider {
    AgentConfirmationValidationContext load(AgentConfirmationAction action, long currentUserId);
}
