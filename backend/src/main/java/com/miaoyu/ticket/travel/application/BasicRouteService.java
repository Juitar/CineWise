package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 用户主动路线查询的应用入口，不缓存也不持久化位置。
 *
 * <p>路线规划依赖任务中的影院标识确认终点仍可用。V013 之前创建的历史任务允许
 * {@code cinemaId} 为 {@code null}，这类任务可以继续展示既有建议，但不能把只含区域名称的
 * 数据当作可用于导航的影院终点。服务必须在读取位置、确认共享或调用 Provider 前拒绝该请求，
 * 从而既不产生误导性路线，也不向第三方发送用户本次提交的起点。</p>
 */
@Service
public class BasicRouteService {

    /** 只读取 D 自己的任务最小投影，不接触订单模块持久化对象。 */
    private final TravelTaskRepository taskRepository;
    /** 由认证上下文提供当前用户，不能使用请求体或 Agent 参数中的 userId。 */
    private final CurrentUserAccessor currentUserAccessor;
    /** Provider 只接收本次请求的起点、影院区域和出行方式。 */
    private final BasicRouteProvider routeProvider;
    /** 统一产生本次路线结果的数据时间，便于测试和过期判断。 */
    private final Clock clock;

    /** 创建路线查询服务；服务本身不持有位置或路线历史。 */
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
     * 查询本人任务并在满足全部隐私与数据完整性条件后调用 Provider。
     *
     * <p>校验顺序不可交换：先确认任务属于当前用户和影院标识存在，再校验用户的第三方共享确认，
     * 最后才读取起点并调用 Provider。这样历史任务缺少影院终点时，即使请求体包含精确位置也不会
     * 被发送出去；originValue 始终是方法局部变量，不进入持久化端口、异常信息或日志。</p>
     */
    public BasicRouteResult planMyRoute(String taskId, BasicRouteCommand command) {
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        if (task.cinemaId() == null) {
            // 历史任务没有可验证的影院终点，只能返回稳定的不可用结果，绝不能按区域名称猜测终点。
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
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
        // 任务归属校验必须先于路线 Provider，避免越权请求或无效任务消耗外部调用额度。
        return taskRepository.findByTaskIdAndUserId(taskId, currentUserAccessor.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
    }

    /**
     * 只在内存中保留本次请求的地点和出行方式；空值统一映射为路线不可用，避免把输入原文带到异常。
     */
    private String requireText(String value, String message) {
        String text = Objects.requireNonNullElse(value, "").trim();
        if (text.isEmpty()) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE, message);
        }
        return text;
    }
}
