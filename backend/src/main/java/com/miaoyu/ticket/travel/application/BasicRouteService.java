package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.CinemaLocationQueryService;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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
    /** Provider 只接收本次请求的两个数值坐标和出行方式。 */
    private final BasicRouteProvider routeProvider;
    /** 影院终点必须由内容模块按 cinemaId 查询，不能用区域文本猜测。 */
    private final java.util.function.LongFunction<java.util.Optional<ResolvedGeoPoint>> cinemaLocationQuery;
    /** 统一产生本次路线结果的数据时间，便于测试和过期判断。 */
    private final Clock clock;

    /** 创建路线查询服务；服务本身不持有位置或路线历史。 */
    @Autowired
    public BasicRouteService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            BasicRouteProvider routeProvider,
            CinemaLocationQueryService cinemaLocationQueryService,
            Clock clock) {
        this(taskRepository, currentUserAccessor, routeProvider, cinemaLocationQueryService::findByCinemaId, clock);
    }

    /** 测试可传入只读位置查询函数；生产环境只使用内容模块提供的公开查询服务。 */
    public BasicRouteService(
            TravelTaskRepository taskRepository,
            CurrentUserAccessor currentUserAccessor,
            BasicRouteProvider routeProvider,
            java.util.function.LongFunction<java.util.Optional<ResolvedGeoPoint>> cinemaLocationQuery,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.currentUserAccessor = currentUserAccessor;
        this.routeProvider = routeProvider;
        this.cinemaLocationQuery = cinemaLocationQuery;
        this.clock = clock;
    }

    /**
     * 查询本人任务并在满足全部隐私与数据完整性条件后调用 Provider。
     *
     * <p>校验顺序不可交换：先确认任务属于当前用户和影院标识存在，再校验用户的第三方共享确认，
     * 最后才读取起点并调用 Provider。这样历史任务缺少影院终点时，即使请求体包含精确位置也不会
     * 被发送出去；起点始终是方法局部变量，不进入持久化端口、异常信息或日志。</p>
     */
    public BasicRouteResult planMyRoute(String taskId, BasicRouteCommand command) {
        RoutePreparation preparation = prepareMyRoute(
                taskId, command.thirdPartySharingConfirmed(), command.travelMode());
        return planPreparedMyRoute(preparation, command.origin());
    }

    /**
     * 在读取浏览器坐标或向第三方发送手动地点前完成路线前置校验。
     *
     * <p>该预检必须由应用服务控制，不能由 HTTP 层自行假定通过。这样未确认共享、越权任务、缺少影院终点
     * 或非法出行方式都会在 Controller 调用地点适配器前失败，避免把不应共享的地点发送给第三方。</p>
     */
    public RoutePreparation prepareMyRoute(
            String taskId, boolean thirdPartySharingConfirmed, String travelMode) {
        TravelTaskRepository.TravelTaskSnapshot task = requireMyTask(taskId);
        if (task.cinemaId() == null) {
            // 历史任务没有可验证的影院终点，只能返回稳定的不可用结果，绝不能按区域名称猜测终点。
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        if (!thirdPartySharingConfirmed) {
            throw new BusinessException(TravelErrorCode.ROUTE_SHARING_NOT_CONFIRMED);
        }
        ResolvedGeoPoint destination = cinemaLocationQuery.apply(task.cinemaId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE));
        return new RoutePreparation(destination, requireTravelMode(travelMode));
    }

    /**
     * 使用已经通过归属、共享和终点校验的上下文执行本次路线调用。
     *
     * <p>起点在此方法返回后即失去引用；不写入任务、日志、缓存或响应。保留此独立入口使 Controller 无法在
     * 预检失败时先执行地理编码。</p>
     */
    public BasicRouteResult planPreparedMyRoute(RoutePreparation preparation, ResolvedGeoPoint origin) {
        RoutePreparation checkedPreparation = Objects.requireNonNull(preparation, "路线预检不能为空");
        ResolvedGeoPoint checkedOrigin = Objects.requireNonNull(origin, "起点不能为空");
        try {
            checkedOrigin.requirePersonalDistanceCapability();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        try {
            return routeProvider.plan(checkedOrigin, checkedPreparation.destination(), checkedPreparation.travelMode(), now)
                    .orElseThrow(() -> new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE));
        } catch (RuntimeException exception) {
            // Provider 异常与空结果对用户都表示路线暂不可用；异常中不得拼接 origin，防止位置泄漏。
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 预检成功后才可携带的短生命周期上下文。
     *
     * <p>构造器仅由本服务使用，防止 Web 层伪造未校验的终点或出行方式。</p>
     */
    public static final class RoutePreparation {

        private final ResolvedGeoPoint destination;
        private final String travelMode;

        private RoutePreparation(ResolvedGeoPoint destination, String travelMode) {
            this.destination = destination;
            this.travelMode = travelMode;
        }

        private ResolvedGeoPoint destination() {
            return destination;
        }

        private String travelMode() {
            return travelMode;
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

    /** 目前只提供驾车和步行，其他模式不能悄悄降级成驾车或固定 Demo。 */
    private String requireTravelMode(String value) {
        String mode = requireText(value, "出行方式不能为空").toUpperCase(Locale.ROOT);
        if (!"DRIVING".equals(mode) && !"WALKING".equals(mode)) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE, "仅支持驾车或步行");
        }
        return mode;
    }
}
