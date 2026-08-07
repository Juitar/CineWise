package com.miaoyu.ticket.travel.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public GetTravelAdviceTool(TravelTaskQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = Objects.requireNonNull(queryService, "出行任务查询服务不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 工具不能为空");
    }

    /**
     * 返回已存在快照的结构化只读摘要。
     *
     * <p>不存在、非法或非本人任务都映射为同一个安全错误码；查询本身不会刷新快照，
     * 因此 Agent 重放和用户重复询问不会触发任何写入。</p>
     */
    public ToolResult<TravelAdviceToolResult> execute(ToolContext context, GetTravelAdviceCommand command) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            return failed(context, CommonErrorCode.INVALID_PARAMETER.code(), "CHECK_TOOL_TARGET");
        }
        if (command == null || command.taskId() == null || command.taskId().isBlank()) {
            return failed(context, TravelErrorCode.TASK_NOT_FOUND.code(), "CHECK_TRAVEL_TASK");
        }
        try {
            TravelTaskQueryService.TravelAdviceSummary summary = queryService.getMyAdviceSummary(command.taskId());
            TravelAdviceToolResult result = toToolResult(summary);
            return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT",
                    result.degraded(), result.fallbackType(), context.stateVersion(), toInstant(result.dataAt()),
                    toInstant(result.expiresAt()));
        } catch (BusinessException exception) {
            // 任务号格式、任务不存在和归属失败都不能透露差异给 Agent 或模型。
            return failed(context, TravelErrorCode.TASK_NOT_FOUND.code(), "CHECK_TRAVEL_TASK");
        }
    }

    private TravelAdviceToolResult toToolResult(TravelTaskQueryService.TravelAdviceSummary summary) {
        JsonNode weatherNode = readJson(summary.weatherJson());
        JsonNode adviceNode = readJson(summary.adviceJson());
        TravelAdviceToolResult.Weather weather = weatherNode == null ? null : new TravelAdviceToolResult.Weather(
                text(weatherNode, "area"), text(weatherNode, "condition"), text(weatherNode, "risk"));
        List<TravelAdviceToolResult.Advice> advice = new ArrayList<>();
        if (weather != null) {
            addAdvice(advice, adviceNode, "WEATHER", "weatherAdvice");
        }
        addAdvice(advice, adviceNode, "TRANSPORT", "transportAdvice");
        return new TravelAdviceToolResult(summary.available(), summary.taskId(), summary.taskStatus().name(), weather,
                advice, summary.source(), toOffsetDateTime(summary.dataTime()), toOffsetDateTime(summary.expiresAt()),
                summary.expired(), summary.degraded(), summary.fallbackType());
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            // 历史快照异常时不把原始 JSON 或解析错误带入 Agent，仅按缺失事实处理。
            return null;
        }
    }

    private static void addAdvice(
            List<TravelAdviceToolResult.Advice> advice, JsonNode node, String type, String field) {
        String value = node == null ? null : text(node, field);
        if (value != null && !value.isBlank()) {
            advice.add(new TravelAdviceToolResult.Advice(type, value));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static java.time.Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime time) {
        // 快照保存的是业务时区本地时间，不能误当成 UTC，否则 Agent 展示的有效期会整体偏移八小时。
        return time == null ? null : time.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    private static ToolResult<TravelAdviceToolResult> failed(ToolContext context, int errorCode, String action) {
        return new ToolResult<>(ToolStatus.FAILED, null, errorCode, false, false, action, false, null,
                context.stateVersion(), null, null);
    }
}
