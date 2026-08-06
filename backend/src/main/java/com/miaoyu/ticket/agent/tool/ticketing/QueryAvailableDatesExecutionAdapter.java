package com.miaoyu.ticket.agent.tool.ticketing;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesTool;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolResult;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将已校验日期查询节点安全转换为 A 的公开日期 Tool 调用。 */
public final class QueryAvailableDatesExecutionAdapter
        extends AbstractTicketingReadToolExecutionAdapter<
                QueryAvailableDatesToolCommand,
                QueryAvailableDatesToolResult> {

    private static final Set<String> INPUT_NAMES = Set.of("movieId", "cinemaId");

    private final QueryAvailableDatesTool queryAvailableDatesTool;

    public QueryAvailableDatesExecutionAdapter(
            QueryAvailableDatesTool queryAvailableDatesTool, ExecutionPlanStateMachine stateMachine) {
        super(
                QueryAvailableDatesTool.TARGET_NAME,
                AgentToolDefinitions.TICKETING_READ_TIMEOUT,
                INPUT_NAMES,
                stateMachine);
        this.queryAvailableDatesTool = Objects.requireNonNull(queryAvailableDatesTool, "日期查询Tool不能为空");
    }

    @Override
    public ToolDefinition definition() {
        return AgentToolDefinitions.queryAvailableDates();
    }

    @Override
    protected QueryAvailableDatesToolCommand createCommand(
            ExecutionPlanNode node, Map<String, InputReference> references) {
        return new QueryAvailableDatesToolCommand(
                requiredSlotValue(node, references, "movieId"),
                requiredSlotValue(node, references, "cinemaId"));
    }

    @Override
    protected ToolResult<QueryAvailableDatesToolResult> executeTool(
            ToolContext context, QueryAvailableDatesToolCommand command) {
        return queryAvailableDatesTool.execute(context, command);
    }
}
