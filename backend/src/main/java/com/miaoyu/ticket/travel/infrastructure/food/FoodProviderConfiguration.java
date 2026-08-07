package com.miaoyu.ticket.travel.infrastructure.food;

import com.miaoyu.ticket.travel.application.FoodPoi;
import com.miaoyu.ticket.travel.application.FoodPoiProvider;
import com.miaoyu.ticket.travel.application.FoodSearchResult;
import com.miaoyu.ticket.travel.application.FoodSearchService.FoodCache;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 真实 Provider 未接入时使用固定 Demo，并明确标记数据来源和降级状态。 */
@Configuration(proxyBeanMethods = false)
public class FoodProviderConfiguration {
    @Bean FoodPoiProvider realFoodPoiProvider() { return (location, radius, now) -> Optional.empty(); }
    @Bean FoodPoiProvider demoFoodPoiProvider() {
        return (location, radius, now) -> Optional.of(new FoodSearchResult(
                List.of(new FoodPoi("影院周边演示餐饮", 300, false, "UNKNOWN")), "DEMO_FOOD_V1", now,
                now.plusMinutes(15), false, true, "DEMO"));
    }
    @Bean FoodCache foodCache() { return new InMemoryFoodCache(); }
}
