package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;

/** 授权时从 B 的当前运行和计划事实重新读取的最小投影。 */
public record AgentActionAuthorizationFacts(
        String runId,
        String planId,
        int planVersion,
        String nodeId,
        String toolName,
        AgentActionParameterHash parameterHash,
        boolean executable) {
}
