package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.ticketing.application.AvailableDateQueryService;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** A 对 Agent 公开的日期查询 Tool API；它复用票务权威查询但不访问 HTTP 层。 */
@Service
public class QueryAvailableDatesTool {
    public static final String TARGET_NAME = "queryAvailableDates";
    private static final Duration FRESHNESS_WINDOW = Duration.ofSeconds(5L);

    private final AvailableDateQueryService availableDateQueryService;
    private final Clock clock;

    public QueryAvailableDatesTool(AvailableDateQueryService availableDateQueryService, Clock clock) {
        this.availableDateQueryService = Objects.requireNonNull(availableDateQueryService, "日期查询服务不能为空");
        this.clock = Objects.requireNonNull(clock, "业务时钟不能为空");
    }

    /** 已知业务错误转换为稳定 ToolResult；未知故障交给 Agent 外层按 traceId 处理。 */
    public ToolResult<QueryAvailableDatesToolResult> execute(
            ToolContext context, QueryAvailableDatesToolCommand command) {
        Objects.requireNonNull(context, "ToolContext不能为空");
        Objects.requireNonNull(command, "查询命令不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            throw new IllegalArgumentException("ToolContext目标与日期查询工具不一致");
        }
        try {
            Instant dataAt = clock.instant();
            QueryAvailableDatesToolResult result = new QueryAvailableDatesToolResult(
                    availableDateQueryService.queryAvailableDates(command.parsedMovieId(), command.parsedCinemaId())
                            .stream()
                            .map(view -> new QueryAvailableDatesToolResult.AvailableDateItem(
                                    view.date(), view.showCount()))
                            .toList());
            return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false,
                    result.dates().isEmpty() ? "CHOOSE_MOVIE_OR_CINEMA" : "CHOOSE_DATE",
                    false, null, context.stateVersion(), dataAt, dataAt.plus(FRESHNESS_WINDOW));
        } catch (BusinessException exception) {
            return knownFailure(context, exception);
        }
    }

    private ToolResult<QueryAvailableDatesToolResult> knownFailure(ToolContext context, BusinessException exception) {
        int code = exception.getErrorCode().code();
        if (code == CommonErrorCode.INVALID_PARAMETER.code()) {
            return failure(context, code, false, false, "CHECK_INPUT");
        }
        if (code == TicketingErrorCode.QUERY_UNAVAILABLE.code()) {
            return failure(context, code, true, false, "RETRY_QUERY");
        }
        throw exception;
    }

    private static ToolResult<QueryAvailableDatesToolResult> failure(
            ToolContext context, int errorCode, boolean retryable, boolean replanSuggested, String nextAction) {
        return new ToolResult<>(ToolStatus.FAILED, null, errorCode, retryable, replanSuggested,
                nextAction, false, null, context.stateVersion(), null, null);
    }
}
