package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 读取本人已有建议摘要；对话查询绝不借此生成快照、发送提醒或请求位置。
 *
 * <p>本人校验在 {@code TravelTaskQueryService} 内完成，工具既不接受 userId，也不绕过认证上下文。
 * 没有快照时返回 available=false 的成功结果，不能为了对话体验临时生成天气或交通建议。
 * 这样 B 重复读取同一任务不会产生任务、快照、通知或位置数据写入。</p>
 */
@Component
public class GetTravelAdviceTool {
    public static final String TARGET_NAME = "getTravelAdvice";
    private final TravelTaskQueryService queryService;

    public GetTravelAdviceTool(TravelTaskQueryService queryService) { this.queryService = queryService; }

    public ToolResult<TravelTaskQueryService.TravelAdviceSummary> execute(ToolContext context, String taskId) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                    "CHECK_TOOL_TARGET", false, null, context.stateVersion(), null, null);
        }
        TravelTaskQueryService.TravelAdviceSummary result = queryService.getMyAdviceSummary(taskId);
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", result.degraded(),
                result.fallbackType(), context.stateVersion(),
                result.dataTime() == null ? null : result.dataTime().toInstant(ZoneOffset.UTC),
                result.expiresAt() == null ? null : result.expiresAt().toInstant(ZoneOffset.UTC));
    }
}
