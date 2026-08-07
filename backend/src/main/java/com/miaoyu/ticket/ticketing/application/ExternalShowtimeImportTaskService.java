package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 异步导入任务编排；HTTP 线程只登记任务，外部 Provider 和座位写入在 Worker 执行。 */
@Service
public class ExternalShowtimeImportTaskService {
    private static final String TASK_ID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private final ExternalShowtimeImportTaskRepository repository;
    private final ExternalShowtimeSandboxImportApplicationService importService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;
    private final Executor executor;

    public ExternalShowtimeImportTaskService(ExternalShowtimeImportTaskRepository repository,
            ExternalShowtimeSandboxImportApplicationService importService, BusinessIdGenerator idGenerator,
            Clock clock, @Qualifier("applicationTaskExecutor") Executor executor) {
        this.repository = repository;
        this.importService = importService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.executor = executor;
    }

    public TaskView create(String clientRequestId, java.time.LocalDate showDate, List<Long> cinemaIds) {
        if (clientRequestId != null) {
            var existing = repository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                schedule(existing.get().taskId());
                return view(existing.get());
            }
        }
        LocalDateTime now = now();
        ExternalShowtimeImportTaskRepository.Task task = new ExternalShowtimeImportTaskRepository.Task(
                idGenerator.nextId(), UUID.randomUUID().toString(), clientRequestId, showDate, cinemaIds,
                ExternalShowtimeImportTaskRepository.TaskStatus.PENDING, null, null, 0, 0, 0, false, null,
                null, null, null, now.plusDays(30), 0, now, now);
        try { repository.create(task); }
        catch (DuplicateKeyException duplicate) {
            return repository.findByClientRequestId(clientRequestId).map(this::view).orElseThrow(() -> duplicate);
        }
        schedule(task.taskId());
        return view(task);
    }

    public TaskView find(String taskId) {
        if (taskId == null || !taskId.matches(TASK_ID_PATTERN)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return repository.findByTaskId(taskId).map(this::view)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    public void recover() {
        repository.requeueExpired(now());
        repository.findNextPending().ifPresent(task -> schedule(task.taskId()));
    }

    private void schedule(String taskId) {
        try { executor.execute(() -> run(taskId)); }
        catch (RuntimeException rejected) { /* 查询仍可看到 PENDING，恢复器稍后重试。 */ }
    }

    private void run(String taskId) {
        String owner = UUID.randomUUID().toString();
        LocalDateTime now = now();
        if (!repository.claim(taskId, owner, now.plusMinutes(5), now)) {
            return;
        }
        ExternalShowtimeImportTaskRepository.Task running = repository.findByTaskId(taskId).orElse(null);
        if (running == null) {
            return;
        }
        try {
            var result = importService.importReferences(running.showDate(), running.cinemaIds());
            ExternalShowtimeImportTaskRepository.TaskStatus status = result.failureCount() == 0
                    ? ExternalShowtimeImportTaskRepository.TaskStatus.SUCCESS
                    : result.successCount() > 0
                        ? ExternalShowtimeImportTaskRepository.TaskStatus.PARTIAL
                        : ExternalShowtimeImportTaskRepository.TaskStatus.FAILED;
            repository.finish(taskId, owner, running.version(),
                    status, result.totalCount(), result.successCount(), result.failureCount(), result.truncated(),
                    result.showIds(), result.errorCode(), now());
        } catch (BusinessException exception) {
            repository.finish(taskId, owner, running.version(),
                    ExternalShowtimeImportTaskRepository.TaskStatus.FAILED, 0, 0, 0, false, null,
                    exception.getErrorCode().code(), now());
        } catch (RuntimeException exception) {
            repository.finish(taskId, owner, running.version(),
                    ExternalShowtimeImportTaskRepository.TaskStatus.FAILED, 0, 0, 0, false, null,
                    CommonErrorCode.INTERNAL_ERROR.code(), now());
        }
    }

    private TaskView view(ExternalShowtimeImportTaskRepository.Task task) {
        return new TaskView(task.taskId(), task.status(), task.totalCount(), task.successCount(),
                task.failureCount(), task.truncated(), task.status().ordinal() >= 2 ? task.showIds() : null,
                task.errorCode(), task.startedAt(), task.finishedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    public record TaskView(String taskId, ExternalShowtimeImportTaskRepository.TaskStatus status, int totalCount,
                           int successCount, int failureCount, boolean truncated, List<Long> showIds,
                           Integer errorCode, LocalDateTime startedAt, LocalDateTime finishedAt) {
        public TaskView { showIds = showIds == null ? null : List.copyOf(showIds); }
    }
}
