package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.geo.application.BrowserUserLocationAdapter;
import com.miaoyu.ticket.geo.application.UserLocationAdapter;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
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
 * <p>Controller 不读取订单、影院或当前用户的持久化对象，只把经过统一校验的一次性起点交给
 * {@link BasicRouteService}。坐标和地点文本都是当前请求的局部变量，不能写入响应、异常、日志、缓存或任务快照。</p>
 */
@RestController
@RequestMapping("/api/v1/travel/tasks")
public class TravelRouteController {

    private final BasicRouteService basicRouteService;
    private final BrowserUserLocationAdapter browserUserLocationAdapter;
    private final UserLocationAdapter manualPlaceLocationAdapter;

    public TravelRouteController(
            BasicRouteService basicRouteService,
            BrowserUserLocationAdapter browserUserLocationAdapter,
            UserLocationAdapter manualPlaceLocationAdapter) {
        this.basicRouteService = basicRouteService;
        this.browserUserLocationAdapter = browserUserLocationAdapter;
        this.manualPlaceLocationAdapter = manualPlaceLocationAdapter;
    }

    /**
     * 根据当前用户的一项出行任务规划驾车或步行路线。
     *
     * <p>位置共享确认由页面在申请浏览器定位或提交手动地点前获得；即使客户端绕过页面，应用层仍会拒绝未确认请求。
     * 不支持的方式、非法坐标、缺失影院坐标和 Provider 失败都只返回稳定的路线不可用结果。</p>
     */
    @PostMapping("/{taskId}/route")
    @Operation(summary = "规划本人出行任务路线")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回不含坐标和路线折线的路线摘要"),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问"),
        @ApiResponse(responseCode = "422", description = "107002 未确认共享；107004 地点无法唯一确定；107005 地点粒度不足"),
        @ApiResponse(responseCode = "503", description = "307001 路线暂不可用")
    })
    public Result<BasicRouteResult> planRoute(
            @PathVariable String taskId, @RequestBody PlanTravelRouteRequest request) {
        if (request == null) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        BasicRouteService.RoutePreparation preparation = basicRouteService.prepareMyRoute(
                taskId, request.thirdPartySharingConfirmed(), request.travelMode());
        ResolvedGeoPoint origin = resolveOrigin(request);
        BasicRouteResult route = basicRouteService.planPreparedMyRoute(preparation, origin);
        return Result.success(route);
    }

    private ResolvedGeoPoint resolveOrigin(PlanTravelRouteRequest request) {
        if (request == null) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        try {
            return switch (RouteOriginType.parse(request.originType())) {
                case CURRENT_LOCATION -> currentLocationOrigin(request);
                case MANUAL_PLACE -> manualPlaceOrigin(request);
            };
        } catch (IllegalArgumentException exception) {
            // 请求坐标绝不写入错误信息，避免统一异常处理器或日志意外保留精确位置。
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
    }

    private ResolvedGeoPoint currentLocationOrigin(PlanTravelRouteRequest request) {
        if (request.placeText() != null) {
            throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
        }
        return browserUserLocationAdapter.fromBrowser(request.longitude(), request.latitude());
    }

    private ResolvedGeoPoint manualPlaceOrigin(PlanTravelRouteRequest request) {
        if (request.longitude() != null || request.latitude() != null) {
            throw new BusinessException(TravelErrorCode.ROUTE_PLACE_UNRESOLVED);
        }
        if (request.placeText() == null || request.placeText().trim().length() > 200) {
            throw new BusinessException(TravelErrorCode.ROUTE_PLACE_UNRESOLVED);
        }
        try {
            ResolvedGeoPoint origin = manualPlaceLocationAdapter.fromPlaceText(request.placeText());
            if (origin.granularity() != LocationGranularity.POI
                    && origin.granularity() != LocationGranularity.ADDRESS) {
                throw new BusinessException(TravelErrorCode.ROUTE_PLACE_TOO_BROAD);
            }
            return origin;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(TravelErrorCode.ROUTE_PLACE_UNRESOLVED);
        }
    }

    /** 当前位置或手动地点只用于本次规划；不包含 userId、影院信息或可持久化位置标识。 */
    public record PlanTravelRouteRequest(
            @Schema(allowableValues = {"CURRENT_LOCATION", "MANUAL_PLACE"}) String originType,
            @Schema(description = "浏览器本次定位的原始经度", example = "112.9388146") BigDecimal longitude,
            @Schema(description = "浏览器本次定位的原始纬度", example = "28.2282085") BigDecimal latitude,
            @Schema(description = "用户主动输入的本次出发地点", example = "长沙市雨花区万家丽中路") String placeText,
            @Schema(allowableValues = {"DRIVING", "WALKING"}, example = "DRIVING") String travelMode,
            @Schema(description = "用户已确认本次起点将发送给路线服务", example = "true")
            boolean thirdPartySharingConfirmed) {

        /** 防止 Spring MVC 的 DEBUG 日志通过 record 默认 toString 泄露地点文本或精确坐标。 */
        @Override
        public String toString() {
            return "PlanTravelRouteRequest[originType=" + originType + ", location=[REDACTED], travelMode="
                    + travelMode + ", thirdPartySharingConfirmed=" + thirdPartySharingConfirmed + "]";
        }
    }

    private enum RouteOriginType {
        CURRENT_LOCATION,
        MANUAL_PLACE;

        private static RouteOriginType parse(String value) {
            if (value == null || value.isBlank()) {
                throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
            }
            try {
                return RouteOriginType.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
            }
        }
    }
}
