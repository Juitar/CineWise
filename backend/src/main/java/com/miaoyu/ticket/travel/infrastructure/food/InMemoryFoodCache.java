package com.miaoyu.ticket.travel.infrastructure.food;

import com.miaoyu.ticket.travel.application.FoodSearchResult;
import com.miaoyu.ticket.travel.application.FoodSearchService.FoodCache;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** MVP 的进程内餐饮缓存；真实 Redis 适配器须保持同样的有效期判断且不得保存用户位置。 */
public class InMemoryFoodCache implements FoodCache {
    private final ConcurrentHashMap<String, FoodSearchResult> entries = new ConcurrentHashMap<>();
    @Override public Optional<FoodSearchResult> findValid(String area, int radius, OffsetDateTime now) {
        return Optional.ofNullable(entries.get(cacheKey(area, radius))).filter(item -> item.expiresAt().isAfter(now));
    }
    @Override public FoodSearchResult save(String area, int radius, FoodSearchResult result) {
        entries.put(cacheKey(area, radius), result);
        return result;
    }
    private String cacheKey(String area, int radius) { return area + ':' + radius; }
}
