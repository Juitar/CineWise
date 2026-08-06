package com.miaoyu.ticket.agent.tool.ticketing;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.ticketing.api.QueryShowsTool;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将已校验场次节点安全转换为 A 的公开场次 Tool 调用。 */
public final class QueryShowsExecutionAdapter
        extends AbstractTicketingReadToolExecutionAdapter<QueryShowsToolCommand, QueryShowsToolResult> {

    private static final Set<String> INPUT_NAMES =
            Set.of("movieId", "cinemaId", "businessDate", "timeFrom", "timeTo");

    private final QueryShowsTool queryShowsTool;

    public QueryShowsExecutionAdapter(QueryShowsTool queryShowsTool, ExecutionPlanStateMachine stateMachine) {
        super(QueryShowsTool.TARGET_NAME, AgentToolDefinitions.TICKETING_READ_TIMEOUT, INPUT_NAMES, stateMachine);
        this.queryShowsTool = Objects.requireNonNull(queryShowsTool, "场次查询Tool不能为空");
    }

    @Override
    public ToolDefinition definition() {
        return AgentToolDefinitions.queryShows();
    }

    @Override
    protected QueryShowsToolCommand createCommand(ExecutionPlanNode node, Map<String, InputReference> references) {
        return new QueryShowsToolCommand(
                requiredSlotValue(node, references, "movieId"),
                requiredSlotValue(node, references, "cinemaId"),
                LocalDate.parse(requiredSlotValue(node, references, "businessDate")),
                parseOptionalTime(optionalSlotValue(node, references, "timeFrom")),
                parseOptionalTime(optionalSlotValue(node, references, "timeTo")));
    }

    @Override
    protected ToolResult<QueryShowsToolResult> executeTool(ToolContext context, QueryShowsToolCommand command) {
        return queryShowsTool.execute(context, command);
    }

    private static LocalTime parseOptionalTime(String value) {
        return value == null ? null : LocalTime.parse(value);
    }
}
