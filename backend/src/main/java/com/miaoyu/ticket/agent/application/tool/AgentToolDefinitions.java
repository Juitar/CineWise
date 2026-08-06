package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanCommand;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolResult;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** B 维护的 Agent 工具白名单定义，不把模型输出解释为 Java 调用目标。 */
public final class AgentToolDefinitions {
    /** D 的推荐工具属于本应用内部只读调用，最多占用三秒预算。 */
    public static final Duration RANK_MOVIE_PLAN_TIMEOUT = Duration.ofSeconds(3L);

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
}
