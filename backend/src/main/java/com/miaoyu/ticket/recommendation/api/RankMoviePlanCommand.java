package com.miaoyu.ticket.recommendation.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.recommendation.application.RecommendationQuery;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * B 调用推荐工具的只读命令。
 *
 * <p>命令只接受已校验的内容和时间条件；showId、价格、座位、库存和 userId 都不能由 Agent 传入。</p>
 */
public record RankMoviePlanCommand(
        String movieId, String cinemaId, LocalDate date, LocalTime timeFrom, LocalTime timeTo) implements ToolCommand {

    /** 复用 Application 查询校验，保证工具入口和直接调用入口对时段的解释一致。 */
    public RankMoviePlanCommand {
        new RecommendationQuery(movieId, cinemaId, date, timeFrom, timeTo);
    }

    /** 将工具专用命令转换为 D 的应用服务输入，不把 Agent 协议带入 Application 层。 */
    public RecommendationQuery toQuery() {
        return new RecommendationQuery(movieId, cinemaId, date, timeFrom, timeTo);
    }
}
