package com.miaoyu.ticket.recommendation.infrastructure;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.recommendation.application.RecommendationQuery;
import com.miaoyu.ticket.recommendation.application.RecommendationShowtimeQueryPort;
import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator.PurchaseCandidate;
import com.miaoyu.ticket.ticketing.application.ShowQuery;
import com.miaoyu.ticket.ticketing.application.ShowQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A 公开场次查询到 D 推荐端口的适配器。
 *
 * <p>只调用 A 的 Application Service；不访问 Controller、Mapper 或票务表，也不在本地复制场次、价格
 * 和库存。</p>
 */
@Component
public class TicketingShowtimeQueryAdapter implements RecommendationShowtimeQueryPort {

    /*
     * 这是 D 读取 A 场次事实的唯一跨模块适配点。
     * 它只调用 A 的 Application Service。
     * basePrice 在此映射为 API 需要的两位小数字符串。
     * expiresAt 完全使用 A 返回的值，不自行计算。
     * 不读取座位、库存或 Mapper。
     * 不缓存为推荐模块的第二份场次数据。
     * 查询异常由应用服务降级处理。
     * 时间转换固定使用业务时区。
     * 业务 ID 在这里不重新生成。
     * 适配器不承担推荐排序。
     * 适配器不承担推荐过滤。
     * 适配器不承担 Agent 路由。
     * 适配器不返回页面组件信息。
     * 适配器不转换为 SSE 事件。
     * 适配器不写日志中的业务详情。
     * 适配器不改变 A 的原始业务 ID。
     * 适配器仅完成字段类型转换。
     */

    private static final String SOURCE_PREFIX = "TICKETING:";

    private final ShowQueryService showQueryService;

    public TicketingShowtimeQueryAdapter(ShowQueryService showQueryService) {
        // 只依赖 A 的公开应用服务，禁止通过本机 HTTP 或 Mapper 访问票务数据。
        this.showQueryService = showQueryService;
    }

    /** A 的 basePrice 仅在此处转为两位小数字符串 price，原始金额不由 D 重算。 */
    @Override
    public List<PurchaseCandidate> querySaleable(RecommendationQuery query) {
        // 对外字符串 ID 在同一应用内部转换为 A 的 long 参数，不改变 API 的字符串约定。
        return showQueryService.queryShows(new ShowQuery(
                        Long.parseLong(query.movieId()),
                        Long.parseLong(query.cinemaId()),
                        query.date(),
                        query.timeFrom(),
                        query.timeTo()))
                .stream()
                // 以下每个字段都来自 A 的 ShowSummaryView，D 不补价格、场次或库存。
                .map(show -> new PurchaseCandidate(
                        Long.toString(show.showId()),
                        Long.toString(show.movieId()),
                        Long.toString(show.cinemaId()),
                        show.basePrice().setScale(2).toPlainString(),
                        toInstant(show.startTime()),
                        toInstant(show.expiresAt()),
                        SOURCE_PREFIX + show.dataType()))
                .toList();
    }

    /** A 的 Application DTO 使用业务本地时间，映射为 Instant 后由 D 用统一 Clock 比较。 */
    private static java.time.Instant toInstant(LocalDateTime value) {
        // 业务时间固定按 Asia/Shanghai 解释，避免部署机时区影响过期判断。
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant();
    }
}
