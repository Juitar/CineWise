package com.miaoyu.ticket.travel.infrastructure.food;

import com.miaoyu.ticket.travel.application.FoodSearchResult;
import com.miaoyu.ticket.travel.application.FoodSearchService.FoodCache;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** MVP 的进程内餐饮缓存；真实 Redis 适配器须保持同样的有效期判断且不得保存用户位置。 */
public class InMemoryFoodCache implements FoodCache {
    private final ConcurrentHashMap<String, FoodSearchResult> entries = new ConcurrentHashMap<>();
    @Override
    public Optional<FoodSearchResult> findValid(ResolvedGeoPoint location, int radius, OffsetDateTime now) {
        return Optional.ofNullable(entries.get(cacheKey(location, radius)))
                .filter(item -> item.expiresAt().isAfter(now));
    }
    @Override public FoodSearchResult save(ResolvedGeoPoint location, int radius, FoodSearchResult result) {
        entries.put(cacheKey(location, radius), result);
        return result;
    }
    /** 影院坐标是静态公开资料；用户坐标从不进入该缓存键。 */
    private String cacheKey(ResolvedGeoPoint location, int radius) {
        return location.longitude().toPlainString() + ',' + location.latitude().toPlainString() + ':' + radius;
    }
}
