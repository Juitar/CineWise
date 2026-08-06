package com.miaoyu.ticket.agent.application.confirmation;

/** 授权拒绝不包含 action 是否存在或归属等可泄露信息；A 统一映射为 205004。 */
public final class AgentActionAuthorizationDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AgentActionAuthorizationDeniedException() {
        super("Agent 确认动作不可用于当前建单");
    }
}
