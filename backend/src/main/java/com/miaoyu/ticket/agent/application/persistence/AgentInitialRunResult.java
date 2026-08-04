package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import java.util.Objects;

/** 初始短事务结果；reused 为真时外层不得再次调用模型或只读工具。 */
public record AgentInitialRunResult(AgentRun run, boolean reused) {

    public AgentInitialRunResult {
        run = Objects.requireNonNull(run, "运行不能为空");
    }
}
