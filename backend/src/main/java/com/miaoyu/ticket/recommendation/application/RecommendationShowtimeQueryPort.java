package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator.PurchaseCandidate;
import java.util.List;

/**
 * 推荐模块读取 A 公开场次事实的唯一端口。
 *
 * <p>端口返回的候选仍要经过 D 的完整性和时效校验；推荐模块不保存、补写或更改其中任何票务字段。</p>
 */
public interface RecommendationShowtimeQueryPort {

    /** 按当前推荐条件读取 A 已确认的可售场次事实。 */
    List<PurchaseCandidate> querySaleable(RecommendationQuery query);
}
