package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 页面调用的本人出行任务接口。
 *
 * <p>Controller 不接收 userId，也不直接读取出行表；应用服务统一用认证上下文校验归属，避免通过任务号
 * 探测其他用户的订单或提醒状态。</p>
 */
@RestController
@RequestMapping("/api/v1/travel/tasks")
public class TravelTaskController {

    private final TravelTaskQueryService travelTaskQueryService;

    public TravelTaskController(TravelTaskQueryService travelTaskQueryService) {
        this.travelTaskQueryService = travelTaskQueryService;
    }

    @GetMapping("/{taskId}")
    public Result<TravelTaskResponse> getTask(@PathVariable String taskId) {
        return Result.success(toResponse(travelTaskQueryService.getMyTask(taskId)));
    }

    @GetMapping("/by-order/{orderId}")
    public Result<TravelTaskResponse> getTaskByOrder(@PathVariable String orderId) {
        return Result.success(toResponse(travelTaskQueryService.getMyTaskByOrderId(orderId)));
    }

    /** 只更新提醒时间；If-Match 与请求 version 同时存在时必须一致，防止旧页面覆盖新设置。 */
    @PutMapping("/{taskId}/reminder")
    public Result<TravelTaskResponse> updateReminder(
            @PathVariable String taskId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateReminderRequest request) {
        long expectedVersion = parseVersion(ifMatch);
        if (expectedVersion != request.version()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "If-Match 与请求版本不一致");
        }
        LocalDateTime triggerAt = request.triggerAt().atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                .toLocalDateTime();
        TravelTaskQueryService.TravelTaskView updated =
                travelTaskQueryService.updateMyReminder(taskId, triggerAt, expectedVersion);
        return Result.success(toResponse(updated));
    }

    /** 页面显式触发的建议刷新，不允许 B 的只读查询复用这个写入口。 */
    @PostMapping("/{taskId}/advice/refresh")
    public Result<TravelAdviceResponse> refreshAdvice(@PathVariable String taskId) {
        return Result.success(toAdviceResponse(travelTaskQueryService.refreshMyAdvice(taskId)));
    }

    private long parseVersion(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "If-Match 必须是非负任务版本");
        }
    }

    private TravelTaskResponse toResponse(TravelTaskQueryService.TravelTaskView task) {
        return new TravelTaskResponse(
                task.taskId(), task.orderId(), task.status().name(), task.triggerAt(), task.version());
    }

    private TravelAdviceResponse toAdviceResponse(
            com.miaoyu.ticket.travel.application.TravelAdviceSnapshot advice) {
        return new TravelAdviceResponse(
                advice.taskVersion(), advice.source(), advice.dataTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID)
                        .toOffsetDateTime(), advice.expiresAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID)
                        .toOffsetDateTime(), advice.isExpired(), advice.degraded(), advice.fallbackType());
    }

    public record UpdateReminderRequest(@NotNull OffsetDateTime triggerAt, @PositiveOrZero long version) {
    }

    public record TravelTaskResponse(
            String taskId, String orderId, String status, OffsetDateTime triggerAt, long version) {
    }

    public record TravelAdviceResponse(
            long taskVersion, String source, OffsetDateTime dataTime, OffsetDateTime expiresAt,
            boolean isExpired, boolean degraded, String fallbackType) {
    }
}
