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

    public TicketingBatchShowtimeQueryAdapter(SaleableShowBatchQueryService service) { this.service = service; }

    /** 只转换 A 已返回的票务事实；不读取 Mapper、Controller 或任何票务表。 */
    @Override
    public BatchResult querySaleable(LocalDate date, Set<Long> cinemaIds) {
        var result = service.query(new SaleableShowBatchQuery(date, cinemaIds));
        List<RankedRecommendationCandidate> candidates = result.shows().stream()
                .map(show -> new RankedRecommendationCandidate(Long.toString(show.movieId()),
                        Long.toString(show.cinemaId()), Long.toString(show.showId()), show.price(),
                        show.startTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(),
                        show.endTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(), List.of(), null,
                        "TICKETING:" + show.dataType(),
                        show.dataAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant(),
                        show.expiresAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toInstant()))
                .toList();
        return new BatchResult(candidates, result.truncated());
    }
}
