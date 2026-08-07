package com.miaoyu.ticket.content.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.AdminContentSyncService;
import com.miaoyu.ticket.content.application.ContentSyncTaskPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员内容同步 REST 适配层。
 *
 * <p>安全配置已统一限制 /api/v1/admin/** 为 ADMIN，POST 继续由 C 的 CSRF 过滤器保护。该类只转换
 * 公开 DTO，绝不读取 SecurityContext、数据库或 Provider 原始响应。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/content")
@Tag(name = "管理员内容同步")
@SecurityRequirement(name = "cookieAuth")
public class AdminContentSyncController {
    private final AdminContentSyncService service;
    private final Clock clock;

    public AdminContentSyncController(AdminContentSyncService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** 返回按来源和城市汇总的最近同步状态，不返回内部城市编号、请求标识或租约。 */
    @GetMapping("/sources")
    @Operation(summary = "查询内容来源同步状态")
    public Result<List<SourceStatusResponse>> sources() {
        return Result.success(service.sourceStatuses().stream().map(this::sourceResponse).toList());
    }

    /**
     * 先受理再同步本轮热映详情。
     *
     * <p>浏览器若在收到响应前断开，必须使用同一个 clientRequestId 查询结果；Controller 不生成请求标识，
     * 因而不会把网络失败伪装成一条新的同步任务。</p>
     */
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "管理员受控同步当前热映资料")
    public Result<SyncTaskResponse> sync(@Valid @RequestBody SyncRequest request) {
        return Result.success(taskResponse(service.requestSync(request.clientRequestId(), request.cityName())));
    }

    /** 结果未知时的唯一恢复入口；不存在的 requestId 返回 100404，不用来源列表猜测。 */
    @GetMapping("/sync/by-request/{clientRequestId}")
    @Operation(summary = "按客户端请求标识查询同步结果")
    public Result<SyncTaskResponse> findByRequest(@PathVariable @NotBlank @Size(max = 64) String clientRequestId) {
        return Result.success(taskResponse(service.queryByRequestId(clientRequestId)));
    }

    private SourceStatusResponse sourceResponse(ContentSyncTaskPort.SourceStatus status) {
        return new SourceStatusResponse(status.provider(), status.resourceType(), status.cityName(),
                status.status().name(), offset(status.startedAt()), offset(status.finishedAt()),
                offset(status.lastSuccessAt()),
                status.successCount(), status.failureCount(),
                status.failureCategory() == null ? null : status.failureCategory().name(), offset(status.dataTime()),
                offset(status.expiresAt()), isExpired(status.expiresAt()),
                "NetStart 仅用于开发和演示学习，不代表票务实时数据或商业授权");
    }

    private SyncTaskResponse taskResponse(AdminContentSyncService.SyncTaskView task) {
        return new SyncTaskResponse(Long.toString(task.syncId()), task.clientRequestId(), task.cityName(),
                task.status().name(), offset(task.startedAt()), offset(task.finishedAt()), task.successCount(),
                task.failureCount(), task.failureCategory() == null ? null : task.failureCategory().name());
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    /** 时效值来自真实快照；没有快照时保持 null，绝不伪造“未过期”。 */
    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.ofInstant(clock.instant(),
                ClockConfiguration.BUSINESS_ZONE_ID));
    }

    /** 请求只包含幂等恢复标识和城市名；ci、地点文本、URL 与 Provider 参数均不接受。 */
    public record SyncRequest(
            @NotBlank @Size(max = 64) @Schema(example = "8c91f1ba-8f43-4fa3-a1d7-1da98afabf73") String clientRequestId,
            @NotBlank @Size(max = 64) @Schema(example = "长沙") String cityName) { }

    /** POST 和按请求查询共用的最小任务状态。 */
    public record SyncTaskResponse(String syncId, String clientRequestId, String cityName,
                                   String status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                   int successCount, int failureCount, String failureCategory) { }

    /** 来源接口不返回行政区码、leaseOwner、原始异常或原始响应。 */
    public record SourceStatusResponse(String provider, String resourceType, String cityName, String status,
                                       OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                       OffsetDateTime lastSuccessAt,
                                       int successCount, int failureCount, String failureCategory,
                                       OffsetDateTime dataTime, OffsetDateTime expiresAt, boolean isExpired,
                                       String licenseNotice) { }
}
