package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.CinemaLocationQueryService;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final java.util.function.LongFunction<java.util.Optional<ResolvedGeoPoint>> cinemaLocationQuery;
    private final Clock clock;

    @Autowired
    public FoodSearchService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            FoodPoiProvider realFoodPoiProvider,
            FoodPoiProvider demoFoodPoiProvider,
            FoodCache foodCache,
            FoodQueryProperties properties,
            CinemaLocationQueryService cinemaLocationQueryService,
            Clock clock) {
        this(taskRepository, currentUserAccessor, realFoodPoiProvider, demoFoodPoiProvider, foodCache, properties,
                cinemaLocationQueryService::findByCinemaId, clock);
    }

    /** 测试可传入只读位置查询函数；生产环境通过内容模块公开服务读取影院位置。 */
    public FoodSearchService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            FoodPoiProvider realFoodPoiProvider,
            FoodPoiProvider demoFoodPoiProvider,
            FoodCache foodCache,
            FoodQueryProperties properties,
            java.util.function.LongFunction<java.util.Optional<ResolvedGeoPoint>> cinemaLocationQuery,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.currentUserAccessor = currentUserAccessor;
        this.realProvider = realFoodPoiProvider;
        this.demoProvider = demoFoodPoiProvider;
        this.foodCache = foodCache;
        this.properties = properties;
        this.cinemaLocationQuery = cinemaLocationQuery;
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
        if (task.cinemaId() == null) {
            throw new BusinessException(TravelErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        ResolvedGeoPoint cinemaLocation = cinemaLocationQuery.apply(task.cinemaId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.DEPENDENCY_UNAVAILABLE));
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        FoodSearchResult result = searchRealSafely(cinemaLocation, radius, now)
                .map(item -> foodCache.save(cinemaLocation, radius, item))
                .or(() -> foodCache.findValid(cinemaLocation, radius, now))
                .or(() -> demoProvider.search(cinemaLocation, radius, now))
                .orElseGet(() -> new FoodSearchResult(List.of(), "UNAVAILABLE", now, now, true, true, "NONE"));
        List<FoodPoi> sorted = result.candidates().stream()
                .sorted(Comparator.comparingInt(FoodPoi::distanceMeters).thenComparing(FoodPoi::name))
                .toList();
        return new FoodSearchResult(sorted, result.source(), result.dataTime(), result.expiresAt(), result.isExpired(),
                result.degraded(), result.fallbackType());
    }

    /** 真实餐饮服务超时或网络失败时视为本次来源不可用，仍要继续缓存和 Demo 回退。 */
    private java.util.Optional<FoodSearchResult> searchRealSafely(
            ResolvedGeoPoint cinemaLocation, int radiusMeters, OffsetDateTime now) {
        try {
            return realProvider.search(cinemaLocation, radiusMeters, now);
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    /** 缓存只按影院静态坐标和半径保存公开餐饮结果，绝不包含用户位置。 */
    public interface FoodCache {
        java.util.Optional<FoodSearchResult> findValid(
                ResolvedGeoPoint cinemaLocation, int radiusMeters, OffsetDateTime now);
        FoodSearchResult save(ResolvedGeoPoint cinemaLocation, int radiusMeters, FoodSearchResult result);
    }
}
