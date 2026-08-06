package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.BasicRouteCommand;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import com.miaoyu.ticket.travel.application.BasicRouteService;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 用户已主动确认共享时才可调用的一次性路线工具；起点只在本次调用栈存活。
 *
 * <p>工具不缓存、不记录也不回传路线折线；调用结束后只有距离和耗时等安全摘要可以离开应用服务。
 * 共享确认和本人任务校验仍由 {@code BasicRouteService} 处理，不能由 Agent 或本工具替用户确认。
 * Provider 不可用时保留稳定错误码，避免把手动地点或坐标写入异常、日志和 Agent 结果。</p>
 */
@Component
public class PlanBasicRouteTool {
    public static final String TARGET_NAME = "planBasicRoute";
    private final BasicRouteService routeService;

    public PlanBasicRouteTool(BasicRouteService routeService) { this.routeService = routeService; }

    public ToolResult<BasicRouteResult> execute(ToolContext context, String taskId, BasicRouteCommand command) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                    "CHECK_TOOL_TARGET", false, null, context.stateVersion(), null, null);
        }
        BasicRouteResult result = routeService.planMyRoute(taskId, command);
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", result.degraded(),
                result.fallbackType(), context.stateVersion(), result.dataTime().toInstant(),
                result.expiresAt().toInstant());
    }
}
