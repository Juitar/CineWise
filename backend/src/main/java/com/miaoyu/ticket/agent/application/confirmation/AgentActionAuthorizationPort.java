package com.miaoyu.ticket.agent.application.confirmation;

/** A 的创建订单 Tool 在自身事务开始前调用的公开确认凭证校验入口。 */
public interface AgentActionAuthorizationPort {
    void authorize(AgentActionAuthorizationRequest request);
}
