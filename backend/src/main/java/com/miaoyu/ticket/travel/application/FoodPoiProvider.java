package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 餐饮 Provider 不接收用户位置，只根据已确认的影院数值坐标查询。 */
@FunctionalInterface
public interface FoodPoiProvider {
    Optional<FoodSearchResult> search(ResolvedGeoPoint cinemaLocation, int radiusMeters, OffsetDateTime requestedAt);
}
