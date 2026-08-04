package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 餐饮 Provider 不接收用户位置，只根据任务已有的影院区域查询。 */
@FunctionalInterface
public interface FoodPoiProvider {
    Optional<FoodSearchResult> search(String cinemaArea, int radiusMeters, OffsetDateTime requestedAt);
}
