package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;

/** 确认事实已经持久化后才允许发布的安全卡片事件出口。 */
public interface AgentConfirmationEventPublisher {
    void publish(AgentConfirmationAction action);
}
