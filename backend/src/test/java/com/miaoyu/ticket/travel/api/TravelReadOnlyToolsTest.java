package com.miaoyu.ticket.travel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.travel.application.FoodSearchResult;
import com.miaoyu.ticket.travel.application.FoodSearchService;
import com.miaoyu.ticket.travel.application.BasicRouteCommand;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import com.miaoyu.ticket.travel.application.BasicRouteService;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import com.miaoyu.ticket.travel.application.WeatherObservation;
import com.miaoyu.ticket.travel.application.WeatherQueryService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 工具测试只验证 D 适配边界：目标名校验、应用服务调用和动态数据时效透传。 */
class TravelReadOnlyToolsTest {
    private static final ToolContext WEATHER_CONTEXT = new ToolContext(
            "run-1", "node-1", GetWeatherTool.TARGET_NAME, List.of(), 3_000L, "trace-1", null, null, 3L);

    @Test
    void shouldReturnWeatherFreshnessWithoutCreatingAnyTravelTask() {
        WeatherQueryService service = mock(WeatherQueryService.class);
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T10:00:00+08:00");
        WeatherObservation weather = new WeatherObservation("西湖区", "多云", "提前出发", "DEMO_WEATHER_V1", now,
                now.plusMinutes(30), false, true, "DEMO");
        when(service.query("西湖区")).thenReturn(weather);

        var result = new GetWeatherTool(service).execute(WEATHER_CONTEXT, new GetWeatherTool.GetWeatherCommand("西湖区"));

        // Demo 结果必须原样带出降级和有效期，B 才能避免把它显示成实时天气。
        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.degraded()).isTrue();
        assertThat(result.expiresAt()).isEqualTo(now.plusMinutes(30).toInstant());
        verify(service).query("西湖区");
    }

    @Test
    void shouldRejectWrongTargetBeforeCallingFoodService() {
        FoodSearchService service = mock(FoodSearchService.class);
        ToolContext wrongContext = new ToolContext(
                "run-1", "node-1", "otherTool", List.of(), 3_000L, "trace-1", null, null, null);

        var result = new SearchNearbyFoodTool(service).execute(wrongContext, "90001", 1000);

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(100001);
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void shouldRejectExactAddressBeforeCallingWeatherService() {
        WeatherQueryService service = mock(WeatherQueryService.class);

        var result = new GetWeatherTool(service).execute(
                WEATHER_CONTEXT, new GetWeatherTool.GetWeatherCommand("文三路168号"));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        verifyNoInteractions(service);
    }

    @Test
    void shouldReadExistingAdviceWithoutRefreshingSnapshot() {
        TravelTaskQueryService service = mock(TravelTaskQueryService.class);
        TravelTaskQueryService.TravelAdviceSummary summary = new TravelTaskQueryService.TravelAdviceSummary(
                "90001", TravelTaskStatus.READY, false, true, null, "{\"transportAdvice\":\"提前出发\"}",
                "DEMO_WEATHER_V1", null, null, true, "DEMO");
        when(service.getMyAdviceSummary("90001")).thenReturn(summary);
        ToolContext context = context(GetTravelAdviceTool.TARGET_NAME);

        var result = new GetTravelAdviceTool(service, new ObjectMapper()).execute(
                context, new GetTravelAdviceCommand("90001"));

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().taskId()).isEqualTo("90001");
        assertThat(result.data().advice()).containsExactly(
                new TravelAdviceToolResult.Advice("TRANSPORT", "提前出发"));
        verify(service).getMyAdviceSummary("90001");
    }

    @Test
    void shouldExposeSnapshotTimeInBusinessOffsetRatherThanTreatingItAsUtc() {
        TravelTaskQueryService service = mock(TravelTaskQueryService.class);
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 7, 18, 30);
        TravelTaskQueryService.TravelAdviceSummary summary = new TravelTaskQueryService.TravelAdviceSummary(
                "90001", TravelTaskStatus.READY, false, true, null, "{\"transportAdvice\":\"提前出发\"}",
                "DEMO_WEATHER_V1", dataTime, dataTime.plusMinutes(30), true, "DEMO");
        when(service.getMyAdviceSummary("90001")).thenReturn(summary);

        var result = new GetTravelAdviceTool(service, new ObjectMapper()).execute(
                context(GetTravelAdviceTool.TARGET_NAME), new GetTravelAdviceCommand("90001"));

        // 快照字段是东八区业务本地时间；标成 UTC 会让 Agent 的展示和过期判断整体提前八小时。
        assertThat(result.data().dataAt()).isEqualTo(OffsetDateTime.parse("2026-08-07T18:30:00+08:00"));
        assertThat(result.data().expiresAt()).isEqualTo(OffsetDateTime.parse("2026-08-07T19:00:00+08:00"));
    }

    @Test
    void shouldHideInvalidOrForeignTravelTaskBehindSameSafeFailure() {
        TravelTaskQueryService service = mock(TravelTaskQueryService.class);
        when(service.getMyAdviceSummary("other-user-task"))
                .thenThrow(new BusinessException(TravelErrorCode.TASK_NOT_FOUND));

        var result = new GetTravelAdviceTool(service, new ObjectMapper()).execute(
                context(GetTravelAdviceTool.TARGET_NAME), new GetTravelAdviceCommand("other-user-task"));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(207001);
        assertThat(result.suggestedNextAction()).isEqualTo("CHECK_TRAVEL_TASK");
        verify(service).getMyAdviceSummary("other-user-task");
    }

    @Test
    void shouldReturnSameSafeFailureForBlankTravelTaskIdWithoutQueryingService() {
        TravelTaskQueryService service = mock(TravelTaskQueryService.class);

        var result = new GetTravelAdviceTool(service, new ObjectMapper()).execute(
                context(GetTravelAdviceTool.TARGET_NAME), new GetTravelAdviceCommand(" "));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(207001);
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnRouteAndFoodAsReadOnlyResults() {
        BasicRouteService routeService = mock(BasicRouteService.class);
        FoodSearchService foodService = mock(FoodSearchService.class);
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T10:00:00+08:00");
        BasicRouteCommand command = new BasicRouteCommand(
                BasicRouteCommand.OriginType.MANUAL, "西湖文化广场", "TRANSIT", true);
        BasicRouteResult route = new BasicRouteResult(
                "AMAP", "TRANSIT", 20, now.plusMinutes(40), "DEMO_ROUTE", now, now.plusMinutes(15), false,
                true, "DEMO");
        FoodSearchResult food = new FoodSearchResult(
                List.of(), "DEMO_FOOD_V1", now, now.plusMinutes(15), false, true, "DEMO");
        when(routeService.planMyRoute("90001", command)).thenReturn(route);
        when(foodService.searchMyFood("90001", 1000)).thenReturn(food);

        var routeResult = new PlanBasicRouteTool(routeService).execute(
                context(PlanBasicRouteTool.TARGET_NAME), "90001", command);
        var foodResult = new SearchNearbyFoodTool(foodService).execute(
                context(SearchNearbyFoodTool.TARGET_NAME), "90001", 1000);

        assertThat(routeResult.data()).isSameAs(route);
        assertThat(foodResult.data()).isSameAs(food);
        verify(routeService).planMyRoute("90001", command);
        verify(foodService).searchMyFood("90001", 1000);
    }

    private ToolContext context(String targetName) {
        return new ToolContext("run-1", "node-1", targetName, List.of(), 3_000L, "trace-1", null, null, 3L);
    }
}
