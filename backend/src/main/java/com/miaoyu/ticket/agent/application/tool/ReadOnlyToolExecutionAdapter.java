package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;

/** 已确认只读工具的类型化执行边界；实现只能调用其固定公开 Tool API。 */
public interface ReadOnlyToolExecutionAdapter {
    String targetName();

    ExecutionResult execute(ExecutionRequest request);

    record ExecutionRequest(
            ExecutionRunState state, String nodeId, String runId, String traceId, long remainingDeadlineMs) {
    }

    record ExecutionResult(ExecutionRunState state, ToolResult<?> toolResult) {
    }
}
