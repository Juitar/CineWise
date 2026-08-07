package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 覆盖餐饮半径边界、Demo 回退和稳定排序，确保查询不变更任务。 */
class FoodSearchServiceTest {

    @Test
    void givenOutOfRangeRadius_whenSearching_thenRejectBeforeCallingProvider() {
        CountingProvider provider = new CountingProvider(Optional.empty());
        assertThatThrownBy(() -> service(provider, provider).searchMyFood("90001", 3001))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(TravelErrorCode.FOOD_RADIUS_OUT_OF_RANGE));
        assertThat(provider.calls).isZero();
    }

    @Test
    void givenRealUnavailable_whenSearching_thenUseSortedDemoCandidates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T08:00:00+08:00");
        CountingProvider real = new CountingProvider(Optional.empty());
        CountingProvider demo = new CountingProvider(Optional.of(new FoodSearchResult(
                List.of(new FoodPoi("乙", 500, false, "UNKNOWN"), new FoodPoi("甲", 500, true, "OPEN")),
                "DEMO_FOOD_V1", now, now.plusMinutes(15), false, true, "DEMO")));

        FoodSearchResult result = service(real, demo).searchMyFood("90001", null);

        assertThat(result.candidates()).extracting(FoodPoi::name).containsExactly("乙", "甲");
        assertThat(result.degraded()).isTrue();
    }

    @Test
    void givenRealProviderThrows_whenSearching_thenFallbackToDemo() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T08:00:00+08:00");
        CountingProvider demo = new CountingProvider(Optional.of(new FoodSearchResult(
                List.of(new FoodPoi("演示餐饮", 300, true, "OPEN")), "DEMO_FOOD_V1", now, now.plusMinutes(15),
                false, true, "DEMO")));
        FoodSearchResult result = service(new ThrowingProvider(), demo).searchMyFood("90001", null);

        assertThat(result.source()).isEqualTo("DEMO_FOOD_V1");
        assertThat(result.fallbackType()).isEqualTo("DEMO");
    }

    @Test
    void givenExpiredRealProviderAfterCachedSuccess_whenSearching_thenReadValidCache() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T08:00:00+08:00");
        CountingProvider real = new CountingProvider(Optional.of(new FoodSearchResult(
                List.of(new FoodPoi("缓存餐饮", 200, true, "OPEN")), "REAL", now, now.plusMinutes(15),
                false, false, null)));
        CountingProvider demo = new CountingProvider(Optional.empty());
        InMemoryCache cache = new InMemoryCache();
        FoodSearchService service = service(real, demo, cache);

        service.searchMyFood("90001", null);
        real.result = Optional.empty();
        FoodSearchResult cached = service.searchMyFood("90001", null);

        assertThat(cached.source()).isEqualTo("REAL");
        assertThat(demo.calls).isZero();
    }

    @Test
    void givenAllProvidersUnavailable_whenSearching_thenReturnEmptyDegradedResult() {
        FoodSearchResult result = service(
                new CountingProvider(Optional.empty()), new CountingProvider(Optional.empty()))
                .searchMyFood("90001", 300);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.source()).isEqualTo("UNAVAILABLE");
        assertThat(result.degraded()).isTrue();
    }

    private FoodSearchService service(FoodPoiProvider real, FoodPoiProvider demo) {
        return service(real, demo, new InMemoryCache());
    }

    private FoodSearchService service(FoodPoiProvider real, FoodPoiProvider demo, InMemoryCache cache) {
        CurrentUserAccessor user = () -> new CurrentUser(1L, RoleCode.USER, 0L);
        return new FoodSearchService(new StubRepository(), user, real, demo, cache,
                new FoodQueryProperties(1000, 300, 3000),
                ignored -> Optional.of(new ResolvedGeoPoint(
                        new java.math.BigDecimal("120.1"), new java.math.BigDecimal("30.2"),
                        LocationGranularity.ADDRESS)),
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    private static final class InMemoryCache implements FoodSearchService.FoodCache {
        private FoodSearchResult value;
        @Override public Optional<FoodSearchResult> findValid(
                ResolvedGeoPoint location, int radius, OffsetDateTime now) {
            return Optional.ofNullable(value).filter(item -> item.expiresAt().isAfter(now));
        }
        @Override public FoodSearchResult save(ResolvedGeoPoint location, int radius, FoodSearchResult result) {
            value = result;
            return result;
        }
    }

    private static final class CountingProvider implements FoodPoiProvider {
        private Optional<FoodSearchResult> result;
        private int calls;
        private CountingProvider(Optional<FoodSearchResult> result) { this.result = result; }
        @Override public Optional<FoodSearchResult> search(ResolvedGeoPoint location, int radius, OffsetDateTime at) {
            calls++;
            return result;
        }
    }

    private static final class ThrowingProvider implements FoodPoiProvider {
        @Override public Optional<FoodSearchResult> search(ResolvedGeoPoint location, int radius, OffsetDateTime at) {
            throw new IllegalStateException("餐饮网络超时");
        }
    }

    private static final class StubRepository implements TravelTaskRepository {
        private final TravelTaskSnapshot task = new TravelTaskSnapshot(1L, "90001", 1L, 2L, 3L, 4L, "西湖区",
                LocalDateTime.of(2026, 8, 5, 19, 0), LocalDateTime.of(2026, 8, 5, 17, 0), 0L, 0L,
                TravelTaskStatus.READY, null, LocalDateTime.of(2026, 8, 4, 0, 0));
        @Override public Optional<TravelTaskSnapshot> findByPaymentEventId(String eventId) {
            return Optional.empty();
        }
        @Override public Optional<TravelTaskSnapshot> findByOrderId(long orderId) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findById(long id) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
            return Optional.of(task);
        }
        @Override public boolean updateTriggerAt(long id, long version, LocalDateTime at, LocalDateTime updatedAt) {
            return false;
        }
        @Override public void insert(NewTravelTask task) { throw new UnsupportedOperationException(); }
    }
}
