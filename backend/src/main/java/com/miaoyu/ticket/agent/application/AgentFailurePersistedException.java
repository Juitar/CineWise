package com.miaoyu.ticket.agent.application;

/** 表示本次 Agent 运行的安全失败事实及可重放事件已经完成持久化。 */
public final class AgentFailurePersistedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AgentFailurePersistedException() {
        super("Agent 安全失败事件已持久化");
    }
}
