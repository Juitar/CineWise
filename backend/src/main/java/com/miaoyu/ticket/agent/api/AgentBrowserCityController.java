package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.location.BrowserCityResolutionService;
import com.miaoyu.ticket.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent 城市确认卡的临时位置入口；接口不创建运行、消息或 SSE 事件。 */
@RestController
@RequestMapping("/api/v1/agent/location")
public class AgentBrowserCityController {

    private final BrowserCityResolutionService cityResolutionService;

    public AgentBrowserCityController(BrowserCityResolutionService cityResolutionService) {
        this.cityResolutionService = cityResolutionService;
    }

    @PostMapping("/city")
    @Operation(summary = "按当前浏览器位置识别城市")
    public Result<CityResponse> resolveCity(@Valid @RequestBody CityRequest request) {
        return Result.success(new CityResponse(
                cityResolutionService.resolveCurrentUserCity(request.longitude(), request.latitude())));
    }

    /** 只接收本次定位坐标；不得传入 userId、会话 ID 或任何 Agent 参数。 */
    public record CityRequest(
            @NotNull @Digits(integer = 3, fraction = 12) @DecimalMin("-180") @DecimalMax("180")
            @Schema(example = "112.938815") BigDecimal longitude,
            @NotNull @Digits(integer = 2, fraction = 12) @DecimalMin("-90") @DecimalMax("90")
            @Schema(example = "28.228209") BigDecimal latitude) { }

    /** 只返回用于用户确认的中文城市名。 */
    public record CityResponse(String city) { }
}
