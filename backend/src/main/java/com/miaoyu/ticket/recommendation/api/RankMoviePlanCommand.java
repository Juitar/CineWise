package com.miaoyu.ticket.recommendation.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.recommendation.application.RecommendationQuery;
import com.miaoyu.ticket.recommendation.domain.RecommendationConstraints;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * B 调用推荐工具的只读命令。
 *
 * <p>命令只接受已校验的内容和时间条件；showId、价格、座位、库存和 userId 都不能由 Agent 传入。</p>
 */
public record RankMoviePlanCommand(
        String cityCode, LocalDate date, int ticketCount, String movieId, String cinemaId, List<String> genres,
        LocalTime timeFrom, LocalTime timeTo, LocalTime latestEndTime, BigDecimal budget,
        List<String> excludedGenres) implements ToolCommand {

    /** 兼容 B 尚未切换的旧槽位命令；新版正式入口使用完整条件构造器。 */
    public RankMoviePlanCommand(String movieId, String cinemaId, LocalDate date, LocalTime timeFrom, LocalTime timeTo) {
        this(null, date, 1, movieId, cinemaId, List.of(), timeFrom, timeTo, null, null, List.of());
    }

    /** 复用 Application 查询校验，保证工具入口和直接调用入口对时段的解释一致。 */
    public RankMoviePlanCommand {
        if (cityCode != null && !cityCode.isBlank()) {
            new RecommendationConstraints(cityCode, date, ticketCount, movieId, cinemaId, genres, timeFrom, timeTo,
                    latestEndTime, budget, excludedGenres);
        } else {
            new RecommendationQuery(movieId, cinemaId, date, timeFrom, timeTo);
        }
    }

    /** 将工具专用命令转换为 D 的应用服务输入，不把 Agent 协议带入 Application 层。 */
    public RecommendationQuery toQuery() {
        return new RecommendationQuery(movieId, cinemaId, date, timeFrom, timeTo);
    }

    /** B 的完整推荐条件只在 D Application 层转换，不把 Agent 命令对象传入领域排序器。 */
    public RecommendationConstraints toConstraints() {
        return new RecommendationConstraints(cityCode, date, ticketCount, movieId, cinemaId, genres, timeFrom, timeTo,
                latestEndTime, budget, excludedGenres);
    }
}
