package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.ticketing.application.ShowQuery;
import com.miaoyu.ticket.ticketing.application.ShowQueryService;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** A 对 Agent 公开的场次查询 Tool API；结果字段与当前公开场次摘要保持一致。 */
@Service
public class QueryShowsTool {
    public static final String TARGET_NAME = "queryShows";
    private static final Duration FRESHNESS_WINDOW = Duration.ofSeconds(5L);

    private final ShowQueryService showQueryService;
    private final Clock clock;

    public QueryShowsTool(ShowQueryService showQueryService, Clock clock) {
        this.showQueryService = Objects.requireNonNull(showQueryService, "场次查询服务不能为空");
        this.clock = Objects.requireNonNull(clock, "业务时钟不能为空");
    }

    /** 不经本应用 HTTP Controller，直接调用 A 的应用服务以保持同进程模块边界。 */
    public ToolResult<QueryShowsToolResult> execute(ToolContext context, QueryShowsToolCommand command) {
        Objects.requireNonNull(context, "ToolContext不能为空");
        Objects.requireNonNull(command, "查询命令不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            throw new IllegalArgumentException("ToolContext目标与场次查询工具不一致");
        }
        try {
            Instant dataAt = clock.instant();
            QueryShowsToolResult result = new QueryShowsToolResult(showQueryService.queryShows(new ShowQuery(
                    command.parsedMovieId(),
                    command.parsedCinemaId(),
                    command.businessDate(),
                    command.timeFrom(),
                    command.timeTo())).stream()
                    .map(view -> new QueryShowsToolResult.ShowItem(
                            Long.toString(view.showId()),
                            Long.toString(view.movieId()),
                            Long.toString(view.cinemaId()),
                            view.cinemaName(),
                            Long.toString(view.auditoriumId()),
                            view.auditoriumName(),
                            view.startTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                            view.endTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                            view.expiresAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                            view.languageVersion(),
                            view.basePrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                            view.availableSeatCount(),
                            view.status(),
                            view.dataType(),
                            view.stateVersion(),
                            view.updatedAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime()))
                    .toList());
            Instant expiresAt = result.shows().stream()
                    .map(QueryShowsToolResult.ShowItem::expiresAt)
                    .map(Instant::from)
                    .min(Instant::compareTo)
                    .map(candidateExpiry -> candidateExpiry.isBefore(dataAt.plus(FRESHNESS_WINDOW))
                            ? candidateExpiry : dataAt.plus(FRESHNESS_WINDOW))
                    .orElse(dataAt.plus(FRESHNESS_WINDOW));
            return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false,
                    result.shows().isEmpty() ? "CHOOSE_MOVIE_OR_CINEMA" : "VIEW_SHOWS",
                    false, null, context.stateVersion(), dataAt, expiresAt);
        } catch (BusinessException exception) {
            return knownFailure(context, exception);
        }
    }

    private ToolResult<QueryShowsToolResult> knownFailure(ToolContext context, BusinessException exception) {
        int code = exception.getErrorCode().code();
        if (code == CommonErrorCode.INVALID_PARAMETER.code()) {
            return failure(context, code, false, false, "CHECK_INPUT");
        }
        if (code == TicketingErrorCode.QUERY_UNAVAILABLE.code()) {
            return failure(context, code, true, false, "RETRY_QUERY");
        }
        throw exception;
    }

    private static ToolResult<QueryShowsToolResult> failure(
            ToolContext context, int errorCode, boolean retryable, boolean replanSuggested, String nextAction) {
        return new ToolResult<>(ToolStatus.FAILED, null, errorCode, retryable, replanSuggested,
                nextAction, false, null, context.stateVersion(), null, null);
    }
}
