package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import java.util.List;
import java.util.Objects;

/** 已持久化运行的安全读取快照，不携带模型原文、异常对象或完整工具响应。 */
public record AgentPersistedRunSnapshot(AgentRun run, List<AgentMessage> messages, List<AgentRunStep> steps) {

    public AgentPersistedRunSnapshot {
        run = Objects.requireNonNull(run, "运行不能为空");
        messages = List.copyOf(Objects.requireNonNull(messages, "消息不能为空"));
        steps = List.copyOf(Objects.requireNonNull(steps, "步骤不能为空"));
    }
}
