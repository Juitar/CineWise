package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** 用户主动查询影院周边餐饮，不读取画像也不修改出行任务。 */
@Service
public class FoodSearchService {

    private final TravelTaskRepository taskRepository;
    private final CurrentUserAccessor currentUserAccessor;
    private final FoodPoiProvider realProvider;
    private final FoodPoiProvider demoProvider;
    private final FoodCache foodCache;
    private final FoodQueryProperties properties;
    private final Clock clock;

    public FoodSearchService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            FoodPoiProvider realFoodPoiProvider,
            FoodPoiProvider demoFoodPoiProvider,
            FoodCache foodCache,
            FoodQueryProperties properties,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.currentUserAccessor = currentUserAccessor;
        this.realProvider = realFoodPoiProvider;
        this.demoProvider = demoFoodPoiProvider;
        this.foodCache = foodCache;
        this.properties = properties;
        this.clock = clock;
    }

    /** 以距离、名称稳定排序，保证页面刷新和 Agent 重读不会随机改变候选顺序。 */
    public FoodSearchResult searchMyFood(String taskId, Integer requestedRadiusMeters) {
        TravelTaskRepository.TravelTaskSnapshot task = taskRepository
                .findByTaskIdAndUserId(taskId, currentUserAccessor.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
        int radius = requestedRadiusMeters == null ? properties.radiusDefaultMeters() : requestedRadiusMeters;
        if (radius < properties.radiusMinMeters() || radius > properties.radiusMaxMeters()) {
            throw new BusinessException(TravelErrorCode.FOOD_RADIUS_OUT_OF_RANGE);
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        FoodSearchResult result = realProvider.search(task.cinemaArea(), radius, now)
                .map(item -> foodCache.save(task.cinemaArea(), radius, item))
                .or(() -> foodCache.findValid(task.cinemaArea(), radius, now))
                .or(() -> demoProvider.search(task.cinemaArea(), radius, now))
                .orElseGet(() -> new FoodSearchResult(List.of(), "UNAVAILABLE", now, now, true, true, "NONE"));
        List<FoodPoi> sorted = result.candidates().stream()
                .sorted(Comparator.comparingInt(FoodPoi::distanceMeters).thenComparing(FoodPoi::name))
                .toList();
        return new FoodSearchResult(sorted, result.source(), result.dataTime(), result.expiresAt(), result.isExpired(),
                result.degraded(), result.fallbackType());
    }

    /** 缓存只按影院区域和半径保存公开餐饮结果，绝不包含用户位置。 */
    public interface FoodCache {
        java.util.Optional<FoodSearchResult> findValid(String cinemaArea, int radiusMeters, OffsetDateTime now);
        FoodSearchResult save(String cinemaArea, int radiusMeters, FoodSearchResult result);
    }
}
