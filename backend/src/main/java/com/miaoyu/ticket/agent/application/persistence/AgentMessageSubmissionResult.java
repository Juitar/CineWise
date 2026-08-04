package com.miaoyu.ticket.agent.application.persistence;

import java.util.Objects;

/** 消息提交结果；重复提交返回既有快照而不是再次执行只读主控。 */
public record AgentMessageSubmissionResult(AgentPersistedRunSnapshot snapshot, boolean reused) {

    public AgentMessageSubmissionResult {
        snapshot = Objects.requireNonNull(snapshot, "运行快照不能为空");
    }
}
