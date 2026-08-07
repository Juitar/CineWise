package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.ticketing.application.AdminExternalShowtimeSandboxImportService;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeSandboxImportApplicationService;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeImportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 管理员手动将 D 的外部排期参考导入为 A 本地沙箱场次。
 *
 * <p>该 Controller 不读取 SecurityContext、D Provider、Mapper 或票务表；权限复核、ID 规范化和
 * 导入编排均委托 A 的应用服务。路径复用 C 已有的 `/api/v1/admin/**` 安全链及 CSRF 保护。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/ticketing/external-showtimes")
@Tag(name = "管理外部排期沙箱导入")
@SecurityRequirement(name = "cookieAuth")
public class AdminExternalShowtimeSandboxImportController {

    private final AdminExternalShowtimeSandboxImportService importService;

    public AdminExternalShowtimeSandboxImportController(AdminExternalShowtimeSandboxImportService importService) {
        this.importService = importService;
    }

    /**
     * 受控导入指定日期和影院范围的候选。
     *
     * <p>没有定时、自动拉取或浏览器重试语义；重复操作由 A 的外部三元键映射返回已有本地场次。
     * 返回值仅暴露本地场次 ID 和截断状态，不泄漏外部供应商响应。</p>
     */
    @PostMapping("/import")
    @Operation(summary = "管理员手动导入本地沙箱场次")
    public Result<ImportResponse> importReferences(@Valid @RequestBody ImportRequest request) {
        ExternalShowtimeImportTaskService.TaskView task = importService.createTask(
                request.showDate(), request.cinemaIds(), request.clientRequestId());
        return Result.success(ImportResponse.from(task));
    }

    @GetMapping("/import/{taskId}")
    @Operation(summary = "查询外部排期沙箱导入任务")
    public Result<ImportResponse> query(@PathVariable String taskId) {
        return Result.success(ImportResponse.from(importService.queryTask(taskId)));
    }

    /** 请求只声明查询范围；票价、座位和库存永远由 A 本地策略生成。 */
    public record ImportRequest(
            @NotNull @Schema(example = "2026-08-10") LocalDate showDate,
            @NotEmpty @Size(max = 100) @Schema(example = "[\"2084825488119652354\"]") List<String> cinemaIds,
            @Schema(description = "网络结果未知时用于恢复原任务") String clientRequestId) {
    }

    /** 不返回外部三元键或原始快照，避免管理 API 成为 D Provider 数据透传接口。 */
    public record ImportResponse(String taskId, String status, int totalCount, int successCount,
                                 int failureCount, boolean truncated, List<String> showIds, Integer errorCode) {
        static ImportResponse from(ExternalShowtimeImportTaskService.TaskView task) {
            return new ImportResponse(task.taskId(), task.status().name(), task.totalCount(), task.successCount(),
                    task.failureCount(), task.truncated(), task.showIds() == null ? null
                            : task.showIds().stream().map(showId -> Long.toString(showId)).toList(), task.errorCode());
        }
    }
}
