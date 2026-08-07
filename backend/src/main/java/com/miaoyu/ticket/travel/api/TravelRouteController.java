package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.geo.application.BrowserUserLocationAdapter;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import com.miaoyu.ticket.travel.application.BasicRouteCommand;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import com.miaoyu.ticket.travel.application.BasicRouteService;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供出行详情页主动发起的一次性路线规划接口。
 *
 * <p>Controller 不读取订单、影院或当前用户的持久化对象，只把经过统一校验的浏览器坐标交给
 * {@link BasicRouteService}。坐标是当前请求的局部变量，不能写入响应、异常、日志、缓存或任务快照。</p>
 */
@RestController
@RequestMapping("/api/v1/travel/tasks")
public class TravelRouteController {

    private final BasicRouteService basicRouteService;
    private final BrowserUserLocationAdapter browserUserLocationAdapter;

    public TravelRouteController(
            BasicRouteService basicRouteService, BrowserUserLocationAdapter browserUserLocationAdapter) {
        this.basicRouteService = basicRouteService;
        this.browserUserLocationAdapter = browserUserLocationAdapter;
    }

    /**
     * 根据当前用户的一项出行任务规划驾车或步行路线。
     *
     * <p>位置共享确认由页面在申请浏览器定位前获得；即使客户端绕过页面，应用层仍会拒绝未确认请求。
     * 不支持的方式、非法坐标、缺失影院坐标和 Provider 失败都只返回稳定的路线不可用结果。</p>
     */
    @PostMapping("/{taskId}/route")
    @Operation(summary = "规划本人出行任务路线")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回不含坐标和路线折线的路线摘要"),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问"),
        @ApiResponse(responseCode = "422", description = "107002 未确认本次位置共享"),
        @ApiResponse(responseCode = "503", description = "307001 路线暂不可用")
    })
    public Result<BasicRouteResult> planRoute(
            @PathVariable String taskId, @RequestBody PlanTravelRouteRequest request) {
        ResolvedGeoPoint origin = resolveOrigin(request);
        BasicRouteResult route = basicRouteService.planMyRoute(
                taskId, new BasicRouteCommand(origin, request.travelMode(), request.thirdPartySharingConfirmed()));
        return Result.success(route);
    }

    private ResolvedGeoPoint resolveOrigin(PlanTravelRouteRequest request) {
        if (request == null) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        try {
            return browserUserLocationAdapter.fromBrowser(request.longitude(), request.latitude());
        } catch (IllegalArgumentException exception) {
            // 请求坐标绝不写入错误信息，避免统一异常处理器或日志意外保留精确位置。
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
    }

    /** 浏览器只提交本次规划所需的数值坐标、方式和共享确认；不包含 userId 或影院信息。 */
    public record PlanTravelRouteRequest(
            @Schema(description = "浏览器本次定位的原始经度", example = "112.9388146") BigDecimal longitude,
            @Schema(description = "浏览器本次定位的原始纬度", example = "28.2282085") BigDecimal latitude,
            @Schema(allowableValues = {"DRIVING", "WALKING"}, example = "DRIVING") String travelMode,
            @Schema(description = "用户已确认本次坐标将发送给路线服务", example = "true")
            boolean thirdPartySharingConfirmed) {
    }
}
