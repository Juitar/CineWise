package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 用户主动路线查询的应用入口，不缓存也不持久化位置。 */
@Service
public class BasicRouteService {

    private final TravelTaskRepository taskRepository;
    private final CurrentUserAccessor currentUserAccessor;
    private final BasicRouteProvider routeProvider;
    private final Clock clock;

    public BasicRouteService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            BasicRouteProvider routeProvider,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.currentUserAccessor = currentUserAccessor;
        this.routeProvider = routeProvider;
        this.clock = clock;
    }

    /**
     * 仅在当前用户确认第三方共享说明后调用 Provider。
     *
     * <p>originValue 是方法局部变量：调用结束即失去引用；不传给任何持久化端口，也不作为异常信息或
     * 日志字段，确保失败和超时时同样不会泄露位置。</p>
     */
    public BasicRouteResult planMyRoute(String taskId, BasicRouteCommand command) {
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        if (!command.thirdPartySharingConfirmed()) {
            throw new BusinessException(TravelErrorCode.ROUTE_SHARING_NOT_CONFIRMED);
        }
        String origin = requireText(command.originValue(), "起点不能为空");
        String mode = requireText(command.travelMode(), "出行方式不能为空");
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        try {
            return routeProvider.plan(origin, task.cinemaArea(), mode, now)
                    .orElseThrow(() -> new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE));
        } catch (RuntimeException exception) {
            // Provider 异常与空结果对用户都表示路线暂不可用；异常中不得拼接 origin，防止位置泄漏。
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
    }

    private TravelTaskRepository.TravelTaskSnapshot requireMyTask(String taskId) {
        return taskRepository.findByTaskIdAndUserId(taskId, currentUserAccessor.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
    }

    private String requireText(String value, String message) {
        String text = Objects.requireNonNullElse(value, "").trim();
        if (text.isEmpty()) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE, message);
        }
        return text;
    }
}
