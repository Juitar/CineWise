package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import com.miaoyu.ticket.agent.domain.tool.DeferredAgentToolCommand;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanCommand;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolResult;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesTool;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolResult;
import com.miaoyu.ticket.ticketing.api.QueryShowsTool;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolResult;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** B 维护的 Agent 工具白名单定义，不把模型输出解释为 Java 调用目标。 */
public final class AgentToolDefinitions {
    /** A 票务查询工具的公开名称；字段和 Application API 等待 A 确认。 */
    public static final String QUERY_AVAILABLE_DATES = "queryAvailableDates";
    public static final String QUERY_SHOWS = "queryShows";
    public static final String QUERY_SEATS = "querySeats";
    /** D 的推荐工具属于本应用内部只读调用，最多占用三秒预算。 */
    public static final Duration RANK_MOVIE_PLAN_TIMEOUT = Duration.ofSeconds(3L);
    /** A 的两个票务查询 Tool 共享五秒上限；具体 Adapter 只能继续缩小剩余预算。 */
    public static final Duration TICKETING_READ_TIMEOUT = Duration.ofSeconds(5L);

    private AgentToolDefinitions() {
    }

    /**
     * 返回 D 已确认的推荐工具定义。
     *
     * <p>字段类型必须和 {@link RankMoviePlanCommand} 保持一致；业务参数不在这里增加 showId、价格、
     * 座位、库存或 userId。</p>
     */
    public static ToolDefinition rankMoviePlan() {
        return new ToolDefinition(
                RankMoviePlanTool.TARGET_NAME,
                RankMoviePlanCommand.class,
                FixedRecommendationResult.class,
                true,
                RANK_MOVIE_PLAN_TIMEOUT,
                false,
                List.of(
                        new ToolInputDefinition("movieId", String.class, true),
                        new ToolInputDefinition("cinemaId", String.class, true),
                        new ToolInputDefinition("date", LocalDate.class, true),
                        new ToolInputDefinition("timeFrom", LocalTime.class, false),
                        new ToolInputDefinition("timeTo", LocalTime.class, false)),
                Set.of(CommonErrorCode.INVALID_PARAMETER.code()));
    }

    /** A 的日期摘要查询仅供 Agent 追问日期，不返回座位或库存锁定事实。 */
    public static ToolDefinition queryAvailableDates() {
        return new ToolDefinition(
                QueryAvailableDatesTool.TARGET_NAME,
                QueryAvailableDatesToolCommand.class,
                QueryAvailableDatesToolResult.class,
                true,
                TICKETING_READ_TIMEOUT,
                false,
                List.of(
                        new ToolInputDefinition("movieId", String.class, true),
                        new ToolInputDefinition("cinemaId", String.class, true)),
                Set.of(CommonErrorCode.INVALID_PARAMETER.code(), TicketingErrorCode.QUERY_UNAVAILABLE.code()));
    }

    /** A 的场次查询只返回当前摘要；用户选择后仍必须进入购票页重新查询权威座位图。 */
    public static ToolDefinition queryShows() {
        return new ToolDefinition(
                QueryShowsTool.TARGET_NAME,
                QueryShowsToolCommand.class,
                QueryShowsToolResult.class,
                true,
                TICKETING_READ_TIMEOUT,
                false,
                List.of(
                        new ToolInputDefinition("movieId", String.class, true),
                        new ToolInputDefinition("cinemaId", String.class, true),
                        new ToolInputDefinition("businessDate", LocalDate.class, true),
                        new ToolInputDefinition("timeFrom", LocalTime.class, false),
                        new ToolInputDefinition("timeTo", LocalTime.class, false)),
                Set.of(CommonErrorCode.INVALID_PARAMETER.code(), TicketingErrorCode.QUERY_UNAVAILABLE.code()));
    }

    /** A 已合入的建单能力只登记为写工具；实际调用仍由确认动作服务独占。 */
    public static ToolDefinition createOrder() {
        return new ToolDefinition(
                CreateOrderTool.TARGET_NAME,
                ConfirmedOrderCommand.class,
                CreateOrderToolResult.class,
                false,
                Duration.ofSeconds(3L),
                true,
                List.of(
                        new ToolInputDefinition("showId", String.class, true),
                        new ToolInputDefinition("seatIds", List.class, true)),
                Set.of(CommonErrorCode.INVALID_PARAMETER.code()));
    }

    /**
     * 为 Owner 联调夹具生成无业务字段的只读定义。
     *
     * <p>该定义不能加入生产默认白名单；正式接入必须替换为 Owner 确认的 Command、Result、输入和错误码。
     */
    public static ToolDefinition deferredReadOnly(String targetName) {
        if (!Set.of(QUERY_AVAILABLE_DATES, QUERY_SHOWS, QUERY_SEATS).contains(targetName)) {
            throw new IllegalArgumentException("未知延期工具: " + targetName);
        }
        return new ToolDefinition(
                targetName,
                DeferredAgentToolCommand.class,
                Void.class,
                true,
                Duration.ofSeconds(5L),
                false,
                List.of(),
                Set.of());
    }
}
