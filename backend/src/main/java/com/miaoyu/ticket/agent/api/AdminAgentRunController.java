package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.audit.AdminAgentRunListQuery;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunPageView;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryService;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunView;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员 Agent 轨迹只读入口；权限由安全链和 Application Service 双重检查。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/agent-runs")
@Tag(name = "管理 Agent 轨迹")
@SecurityRequirement(name = "cookieAuth")
public class AdminAgentRunController {
    private final AdminAgentRunQueryService queryService;

    public AdminAgentRunController(AdminAgentRunQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "分页查询脱敏 Agent 运行轨迹")
    public Result<PageResult<AdminAgentRunSummaryResponse>> queryRuns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userKeyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startedTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AdminAgentRunPageView result = queryService.queryRuns(
                new AdminAgentRunListQuery(status, userKeyword, startedFrom, startedTo, page, size));
        return Result.success(new PageResult<>(result.total(), result.page(), result.size(),
                result.records().stream().map(this::summary).toList()));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询脱敏 Agent 运行详情")
    public Result<AdminAgentRunDetailResponse> queryRun(@PathVariable @NotBlank @Size(max = 64) String runId) {
        return Result.success(detail(queryService.queryRun(runId)));
    }

    private AdminAgentRunSummaryResponse summary(AdminAgentRunView view) {
        return new AdminAgentRunSummaryResponse(view.runId(), view.sessionId(), view.userDisplay(), view.status(),
                view.planId(), view.planVersion(), view.nodeCount(), view.completedNodeCount(), view.failedNodeCount(),
                offset(view.startedAt()), offset(view.finishedAt()), view.durationMs(), view.errorCode(),
                view.errorSummary());
    }

    private AdminAgentRunDetailResponse detail(AdminAgentRunView view) {
        return new AdminAgentRunDetailResponse(view.runId(), view.sessionId(), view.userDisplay(), view.status(),
                view.planId(), view.planVersion(), view.nodeCount(), view.completedNodeCount(), view.failedNodeCount(),
                offset(view.startedAt()), offset(view.finishedAt()), view.durationMs(), view.errorCode(),
                view.errorSummary(),
                view.nodes().stream().map(node -> new AdminAgentRunDetailResponse.NodeResponse(node.nodeId(),
                        node.nodeType(), node.targetName(), node.status(), node.attemptCount(),
                        offset(node.startedAt()),
                        offset(node.finishedAt()), node.durationMs(), node.toolStatus(), node.errorCode(),
                        node.errorSummary(), node.recoveryHint())).toList());
    }

    private static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
