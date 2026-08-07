package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.AgentDistanceRecommendationApplicationService;
import com.miaoyu.ticket.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 距离推荐 HTTP 入口只转发已校验请求，不接触 D 的位置服务。 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentDistanceRecommendationController {
    private final AgentDistanceRecommendationApplicationService applicationService;

    public AgentDistanceRecommendationController(AgentDistanceRecommendationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/sessions/{sessionId}/distance-recommendation-runs")
    public Result<AgentDistanceRunResponse> initialize(
            @PathVariable String sessionId, @Valid @RequestBody AgentDistanceRunInitializeRequest request) {
        return Result.success(applicationService.initialize(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/runs/by-client-request/{clientRequestId}")
    public Result<AgentDistanceRunResponse> findByClientRequest(
            @PathVariable String sessionId, @PathVariable String clientRequestId) {
        return Result.success(applicationService.findByClientRequest(sessionId, clientRequestId));
    }

    @PostMapping("/sessions/{sessionId}/runs/{runId}/distance-context")
    public Result<AgentDistanceContextResponse> createContext(
            @PathVariable String sessionId, @PathVariable String runId) {
        return Result.success(applicationService.createContext(sessionId, runId));
    }

    @PostMapping("/runs/{runId}/distance-recommendation")
    public Result<AgentDistanceRunResponse> submitLocationResult(
            @PathVariable String runId, @Valid @RequestBody AgentDistanceRecommendationResultRequest request) {
        return Result.success(applicationService.submitLocationResult(runId, request));
    }
}
