package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.recommendation.domain.RankedRecommendationCandidate;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 推荐读取 A 批量可售场次的唯一端口；返回值不在 D 侧持久化。 */
public interface RecommendationBatchShowtimeQueryPort {
    BatchResult querySaleable(LocalDate date, Set<Long> cinemaIds);

    /** truncated 必须保留，让排序方知道候选集不是完整全集。 */
    record BatchResult(List<RankedRecommendationCandidate> candidates, boolean truncated) {
        public BatchResult { candidates = List.copyOf(candidates); }
    }
}
