package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import java.util.List;
import java.util.Objects;

/** 一次可开始节点选择的结果，同时返回已处理阻塞分支的运行快照。 */
public record RunnableNodeSelection(ExecutionRunState state, List<ExecutionPlanNode> nodes) {

    public RunnableNodeSelection {
        Objects.requireNonNull(state, "运行状态不能为空");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "可开始节点不能为空"));
    }
}
