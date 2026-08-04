package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人任务的只读与提醒时间更新入口。
 *
 * <p>建议尚未生成时明确返回 {@code available=false}；生成后返回最新不可变快照的安全摘要。无论哪种
 * 情况都必须先完成本人校验，避免通过任务号读取其他用户的出行信息。</p>
 */
@Service
public class TravelTaskQueryService {

    private final TravelTaskRepository travelTaskRepository;
    private final CurrentUserAccessor currentUserAccessor;
    private final TravelAdviceRepository travelAdviceRepository;
    private final TravelAdviceService travelAdviceService;
    private final Clock clock;

    public TravelTaskQueryService(
            TravelTaskRepository travelTaskRepository,
            CurrentUserAccessor currentUserAccessor,
            TravelAdviceRepository travelAdviceRepository,
            TravelAdviceService travelAdviceService,
            Clock clock) {
        this.travelTaskRepository = travelTaskRepository;
        this.currentUserAccessor = currentUserAccessor;
        this.travelAdviceRepository = travelAdviceRepository;
        this.travelAdviceService = travelAdviceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TravelTaskView getMyTask(String taskId) {
        return toView(requireMyTask(taskId));
    }

    @Transactional(readOnly = true)
    public TravelTaskView getMyTaskByOrderId(String orderId) {
        long parsedOrderId = parsePositiveId(orderId);
        TravelTaskRepository.TravelTaskSnapshot task = travelTaskRepository.findByOrderId(parsedOrderId)
                .filter(candidate -> candidate.userId() == currentUserAccessor.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
        return toView(task);
    }

    /** B 后续工具只能调用此只读入口，不能借查询触发刷新、提醒或位置请求。 */
    @Transactional(readOnly = true)
    public TravelAdviceSummary getMyAdviceSummary(String taskId) {
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        return travelAdviceRepository.findLatestByTaskId(task.id())
                .map(snapshot -> toAdviceSummary(task, snapshot))
                .orElseGet(() -> new TravelAdviceSummary(task.taskId(), task.status(), task.status().isTerminal(),
                        false, null, null, null, null, null, false, null));
    }

    /**
     * 用户主动刷新只在建议可读时允许，避免高频请求反复访问外部天气来源。
     *
     * <p>刷新间隔按任务最近更新时间判断；任务取消时先返回 207002，绝不生成新的快照。</p>
     */
    @Transactional
    public TravelAdviceSnapshot refreshMyAdvice(String taskId) {
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        if (task.status() == TravelTaskStatus.CANCELLED) {
            throw new BusinessException(TravelErrorCode.TASK_CANCELLED);
        }
        if (task.status() != TravelTaskStatus.READY && task.status() != TravelTaskStatus.NOTIFIED) {
            throw new BusinessException(TravelErrorCode.TASK_VERSION_CONFLICT);
        }
        if (Duration.between(task.updatedAt(), currentBusinessTime()).compareTo(Duration.ofMinutes(5)) < 0) {
            throw new BusinessException(TravelErrorCode.REFRESH_TOO_FREQUENT);
        }
        return travelAdviceService.generate(task.id());
    }

    private TravelAdviceSummary toAdviceSummary(
            TravelTaskRepository.TravelTaskSnapshot task, TravelAdviceSnapshot snapshot) {
        boolean expired = task.status() == TravelTaskStatus.CANCELLED || snapshot.isExpired();
        return new TravelAdviceSummary(task.taskId(), task.status(), expired, true, snapshot.weatherJson(),
                snapshot.adviceJson(), snapshot.source(), snapshot.dataTime(), snapshot.expiresAt(),
                snapshot.degraded(), snapshot.fallbackType());
    }

    /**
     * 修改提醒时间使用版本条件更新，避免两个页面标签相互覆盖。
     *
     * <p>取消由 A 的退款事件决定；用户请求不能把取消任务重新打开，因此应返回固定的 207002。</p>
     */
    @Transactional
    public TravelTaskView updateMyReminder(String taskId, LocalDateTime triggerAt, long expectedVersion) {
        if (triggerAt == null || expectedVersion < 0) {
            throw new BusinessException(TravelErrorCode.TASK_VERSION_CONFLICT);
        }
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        if (task.status() == TravelTaskStatus.CANCELLED) {
            throw new BusinessException(TravelErrorCode.TASK_CANCELLED);
        }
        if (task.status().isTerminal()) {
            throw new BusinessException(TravelErrorCode.TASK_VERSION_CONFLICT);
        }
        boolean updated = travelTaskRepository.updateTriggerAt(
                task.id(), expectedVersion, triggerAt, currentBusinessTime());
        if (!updated) {
            throw new BusinessException(TravelErrorCode.TASK_VERSION_CONFLICT);
        }
        return getMyTask(taskId);
    }

    private TravelTaskRepository.TravelTaskSnapshot requireMyTask(String taskId) {
        if (taskId == null || !taskId.matches("[1-9][0-9]*")) {
            throw new BusinessException(TravelErrorCode.TASK_NOT_FOUND);
        }
        return travelTaskRepository.findByTaskIdAndUserId(taskId, currentUserAccessor.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
    }

    private long parsePositiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new BusinessException(TravelErrorCode.TASK_NOT_FOUND);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(TravelErrorCode.TASK_NOT_FOUND);
        }
    }

    private TravelTaskView toView(TravelTaskRepository.TravelTaskSnapshot task) {
        return new TravelTaskView(
                task.taskId(),
                Long.toString(task.orderId()),
                task.status(),
                task.triggerAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                task.version());
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /** 出行任务 REST 的最小数据；内部 ID、影院地址和精确位置不对外暴露。 */
    public record TravelTaskView(
            String taskId,
            String orderId,
            TravelTaskStatus status,
            java.time.OffsetDateTime triggerAt,
            long version) {
    }

    /** 快照未生成时明确 available=false，不能把空建议冒充天气或实时路线。 */
    public record TravelAdviceSummary(
            String taskId,
            TravelTaskStatus taskStatus,
            boolean expired,
            boolean available,
            String weatherJson,
            String adviceJson,
            String source,
            LocalDateTime dataTime,
            LocalDateTime expiresAt,
            boolean degraded,
            String fallbackType) {
    }
}
