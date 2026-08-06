package com.miaoyu.ticket.recommendation.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.recommendation.application.DistanceContextService;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** C 上传一次性定位的 REST 入口；位置不会向 Agent 或持久层转发。 */
@RestController
@RequestMapping("/api/v1/recommendation/distance-contexts")
public class DistanceContextController {
    private final DistanceContextService distanceContextService;

    public DistanceContextController(DistanceContextService distanceContextService) {
        this.distanceContextService = distanceContextService;
    }

    @PostMapping("/{distanceContextId}/location")
    public Result<Void> upload(@PathVariable String distanceContextId, @RequestBody LocationRequest request) {
        DistanceContextService.UploadResult result;
        try {
            result = distanceContextService.upload(distanceContextId, request.longitude(), request.latitude());
        } catch (IllegalArgumentException exception) {
            // 经纬度范围错误属于客户端参数错误，不能让领域校验异常落入全局 500。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (result == DistanceContextService.UploadResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (result == DistanceContextService.UploadResult.CONFLICT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return Result.success();
    }

    public record LocationRequest(BigDecimal longitude, BigDecimal latitude) { }
}
