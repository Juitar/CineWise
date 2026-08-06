package com.miaoyu.ticket.travel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 页面使用的本人出行 REST 契约；Controller 只做参数校验、应用服务调用和响应映射。 */
@RestController
@RequestMapping("/api/v1/travel/tasks")
public class TravelTaskController {

    private final TravelTaskQueryService travelTaskQueryService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TravelTaskController(TravelTaskQueryService travelTaskQueryService, ObjectMapper objectMapper) {
        this.travelTaskQueryService = travelTaskQueryService;
        this.objectMapper = objectMapper;
    }

    public TravelTaskController(TravelTaskQueryService travelTaskQueryService) {
        this(travelTaskQueryService, new ObjectMapper());
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询本人出行任务详情")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回订单、影片和影院公开摘要",
                content = @Content(examples = @ExampleObject(name = "task-detail-success",
                        value = "{\"code\":0,\"data\":{\"taskId\":\"90001\",\"status\":\"READY\",\"order\":{},\"movie\":{},\"cinema\":{}}}"))),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问"),
        @ApiResponse(responseCode = "503", description = "207004 订单或内容摘要不可用")
    })
    public Result<TravelTaskResponse> getTask(@PathVariable String taskId) {
        return Result.success(toResponse(travelTaskQueryService.getMyTaskDetails(taskId)));
    }

    @GetMapping("/by-order/{orderId}")
    /** 按订单查询仍先经过本人任务校验，再复用同一详情聚合，避免暴露订单是否属于他人。 */
    public Result<TravelTaskResponse> getTaskByOrder(@PathVariable String orderId) {
        TravelTaskQueryService.TravelTaskView task = travelTaskQueryService.getMyTaskByOrderId(orderId);
        return Result.success(toResponse(travelTaskQueryService.getMyTaskDetails(task.taskId())));
    }

    @GetMapping("/{taskId}/advice")
    @Operation(summary = "查询本人出行建议")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回类型化天气和交通建议",
                content = @Content(examples = @ExampleObject(name = "advice-weather-normal",
                        value = "{\"code\":0,\"data\":{\"available\":true,\"taskId\":\"90001\",\"taskStatus\":\"READY\",\"weather\":{\"condition\":\"多云\"},\"advice\":[{\"type\":\"TRANSPORT\",\"text\":\"提前到场\"}],\"source\":\"AMAP_WEATHER\",\"isExpired\":false,\"degraded\":false}}"))),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问")
    })
    public Result<TravelAdviceResponse> getAdvice(@PathVariable String taskId) {
        // 建议查询只读快照；未生成时返回 available=false，不在 GET 中触发 Provider。
        return Result.success(toAdviceResponse(travelTaskQueryService.getMyAdviceSummary(taskId)));
    }

    @PutMapping("/{taskId}/reminder")
    public Result<TravelTaskResponse> updateReminder(
            @PathVariable String taskId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateReminderRequest request) {
        // If-Match 和请求体版本必须同时匹配，防止旧页面覆盖用户刚修改的提醒时间。
        long expectedVersion = parseVersion(ifMatch);
        if (expectedVersion != request.version()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "If-Match 与请求版本不一致");
        }
        LocalDateTime triggerAt = request.triggerAt().atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                .toLocalDateTime();
        travelTaskQueryService.updateMyReminder(taskId, triggerAt, expectedVersion);
        return Result.success(toResponse(travelTaskQueryService.getMyTaskDetails(taskId)));
    }

    @PostMapping("/{taskId}/advice/refresh")
    @Operation(summary = "刷新本人出行建议")
    @ApiResponses({
        @ApiResponse(responseCode = "409", description = "207002 已取消；207003 状态或版本冲突"),
        @ApiResponse(responseCode = "429", description = "107001 五分钟内重复刷新")
    })
    public Result<TravelAdviceResponse> refreshAdvice(@PathVariable String taskId) {
        // 刷新由 Application Service 统一判断状态、版本和五分钟频率，Controller 不自行放宽条件。
        travelTaskQueryService.refreshMyAdvice(taskId);
        return Result.success(toAdviceResponse(travelTaskQueryService.getMyAdviceSummary(taskId)));
    }

    private long parseVersion(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "If-Match 必须是非负任务版本");
        }
    }

    private TravelTaskResponse toResponse(TravelTaskQueryService.TravelTaskDetails task) {
        return new TravelTaskResponse(task.taskId(), task.status().name(), task.triggerAt(), task.version(),
                new OrderResponse(task.order().orderId(), task.order().orderNo(), task.order().showId(),
                        task.order().showStartTime()),
                new MovieResponse(task.movie().movieId(), task.movie().title(), task.movie().posterUrl(),
                        task.movie().source(), toOffsetDateTime(task.movie().dataTime())),
                new CinemaResponse(task.cinema().cinemaId(), task.cinema().name(), task.cinema().area(),
                        task.cinema().address(), task.cinema().source(), toOffsetDateTime(task.cinema().dataTime()),
                        toOffsetDateTime(task.cinema().expiresAt()), task.cinema().isExpired()));
    }

    private TravelAdviceResponse toAdviceResponse(TravelTaskQueryService.TravelAdviceSummary advice) {
        JsonNode weatherNode = readJson(advice.weatherJson());
        JsonNode adviceNode = readJson(advice.adviceJson());
        WeatherResponse weather = weatherNode == null ? null
                : new WeatherResponse(text(weatherNode, "area"), text(weatherNode, "condition"),
                        text(weatherNode, "risk"));
        List<AdviceItem> items = new ArrayList<>();
        addAdvice(items, adviceNode, "WEATHER", "weatherAdvice");
        addAdvice(items, adviceNode, "TRANSPORT", "transportAdvice");
        OffsetDateTime dataAt = toOffsetDateTime(advice.dataTime());
        return new TravelAdviceResponse(advice.available(), advice.taskId(), advice.taskStatus().name(), weather,
                List.copyOf(items), advice.source(), dataAt, dataAt,
                toOffsetDateTime(advice.expiresAt()), advice.expired(), advice.degraded(), advice.fallbackType(),
                advice.weatherJson(), advice.adviceJson());
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 兼容旧快照解析失败时只丢弃该对象，不把内部 JSON 解析异常暴露给 C。
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void addAdvice(List<AdviceItem> items, JsonNode node, String type, String field) {
        String value = node == null ? null : text(node, field);
        // 天气缺失不影响通用交通建议，因此每一类建议独立判断是否加入数组。
        if (value != null && !value.isBlank()) {
            items.add(new AdviceItem(type, value));
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime time) {
        return time == null ? null : time.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    public record UpdateReminderRequest(@NotNull OffsetDateTime triggerAt, @PositiveOrZero long version) {
    }

    public record TravelTaskResponse(
            String taskId, String status, OffsetDateTime triggerAt, long version,
            OrderResponse order, MovieResponse movie, CinemaResponse cinema) {
    }

    public record OrderResponse(String orderId, String orderNo, String showId, OffsetDateTime showStartTime) {
    }

    public record MovieResponse(String movieId, String title, String posterUrl, String source, OffsetDateTime dataAt) {
    }

    public record CinemaResponse(String cinemaId, String name, String area, String address, String source,
                                 OffsetDateTime dataAt, OffsetDateTime expiresAt, boolean isExpired) {
    }

    public record TravelAdviceResponse(
            boolean available, String taskId, String taskStatus, WeatherResponse weather, List<AdviceItem> advice,
            String source, OffsetDateTime dataAt, OffsetDateTime dataTime, OffsetDateTime expiresAt, boolean isExpired,
            boolean degraded,
            String fallbackType, @Deprecated String weatherJson, @Deprecated String adviceJson) {
    }

    public record WeatherResponse(String area, String condition, String risk) {
    }

    public record AdviceItem(String type, String text) {
    }
}
