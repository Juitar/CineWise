package com.miaoyu.ticket.content.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.content.application.CityResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 城市解析只接收请求体，避免地点文本进入 URL、浏览器历史或代理访问日志。
 * Controller 不记录请求体，也不把原始地点文本交给持久化、缓存或 Agent 轨迹。
 */
@RestController
@RequestMapping("/api/v1/content/cities")
public class ContentCityResolutionController {

    private final CityResolutionService service;

    public ContentCityResolutionController(CityResolutionService service) {
        this.service = service;
    }

    @PostMapping("/resolve")
    @Operation(summary = "将临时地点文本解析为受控城市名")
    @SecurityRequirement(name = "cookieAuth")
    public Result<CityResolutionResponse> resolve(@RequestBody CityResolutionRequest request) {
        CityResolutionService.CityResolution result = service.resolve(request == null ? null : request.locationText());
        return Result.success(new CityResolutionResponse(result.status().name(), result.cityName()));
    }

    /** locationText 只在当前请求方法的栈内存在，不能在接口外复用或保存。 */
    public record CityResolutionRequest(
            @Schema(description = "临时地点文本，仅用于本次解析", example = "湖南省长沙市岳麓区") String locationText) { }

    /** 公开响应只给 C 可保存的标准城市名，绝不包含候选列表或 Provider 城市标识。 */
    public record CityResolutionResponse(
            @Schema(allowableValues = {"RESOLVED", "UNRECOGNIZED", "SELECTION_REQUIRED"}) String status,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @Schema(nullable = true, example = "长沙") String cityName) { }
}
