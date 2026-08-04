package com.miaoyu.ticket.agent.domain.persistence;

/** V008 中一次 Agent 运行的持久化状态。 */
public enum AgentRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** 判断当前运行是否已经写入终态。 */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
