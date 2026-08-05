package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;

/** 生产实现从 B 自有持久化读取当前运行/计划事实，不能读取 A 的交易表。 */
public interface AgentActionAuthorizationFactsProvider {
    AgentActionAuthorizationFacts load(AgentConfirmationAction action);
}
