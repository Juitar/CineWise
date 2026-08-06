package com.miaoyu.ticket.recommendation.infrastructure;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.recommendation.application.RecommendationBatchShowtimeQueryPort;
import com.miaoyu.ticket.recommendation.domain.RankedRecommendationCandidate;
import com.miaoyu.ticket.ticketing.application.SaleableShowBatchQuery;
import com.miaoyu.ticket.ticketing.application.SaleableShowBatchQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** A 批量可售场次 Application API 到 D 推荐端口的字段适配。 */
@Component
public class TicketingBatchShowtimeQueryAdapter implements RecommendationBatchShowtimeQueryPort {
    private final SaleableShowBatchQueryService service;

    /**
     * A 的公开查询服务是场次、价格、余座和来源事实的唯一入口。
     *
     * <p>D 仅保留返回值供本轮推荐排序使用，不缓存、不写库，也不从票务持久层补查任何字段。</p>
     */
    public TicketingBatchShowtimeQueryAdapter(SaleableShowBatchQueryService service) {
        this.service = service;
    }

    /** 只转换 A 已返回的票务事实；不读取 Mapper、Controller 或任何票务表。 */
    @Override
    public BatchResult querySaleable(LocalDate date, Set<Long> cinemaIds) {
        // 城市到影院 ID 的解析在 D 内容服务完成；A 只接收本地影院 ID 和日期。
        // 空列表、查询不可用和数量上限均由 A 的公开服务按既定语义处理。
        // D 不吞掉 306003，避免把票务查询故障误报为没有可购方案。
        var result = service.query(new SaleableShowBatchQuery(date, cinemaIds));
        List<RankedRecommendationCandidate> candidates = result.shows().stream()
                // dataType 说明数据大类；source 说明具体生成者，例如 demo-seed。
                // 两者都要保留，避免把不同演示来源或未来真实来源混成同一种候选。
                // 该字符串只用于来源展示和诊断，不能作为订单、座位或库存判断依据。
                .map(show -> new RankedRecommendationCandidate(Long.toString(show.movieId()),
                        Long.toString(show.cinemaId()), Long.toString(show.showId()), show.price(),
                        show.startTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(),
                        show.endTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(), List.of(), null,
                        show.availableSeatCount(),
                        "TICKETING:" + show.dataType() + ":" + show.source(),
                        show.dataAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(),
                        show.expiresAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant()))
                .toList();
        // truncated 必须原样透传，推荐层据此标记候选集可能不完整。
        return new BatchResult(candidates, result.truncated());
    }
}
