package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** A 异步外部排期导入任务的持久化端口。 */
public interface ExternalShowtimeImportTaskRepository {
    void create(Task task);
    Optional<Task> findByTaskId(String taskId);
    Optional<Task> findByClientRequestId(String clientRequestId);
    Optional<Task> findNextPending();
    boolean claim(String taskId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now);
    boolean finish(String taskId, String leaseOwner, long version, TaskStatus status, int totalCount,
                   int successCount, int failureCount, boolean truncated, List<Long> showIds,
                   Integer errorCode, LocalDateTime finishedAt);
    int requeueExpired(LocalDateTime now);

    record Task(long id, String taskId, String clientRequestId, LocalDate showDate, List<Long> cinemaIds,
                TaskStatus status, String leaseOwner, LocalDateTime leaseUntil, int totalCount,
                int successCount, int failureCount, boolean truncated, List<Long> showIds, Integer errorCode,
                LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime expireAt, long version,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        public Task {
            cinemaIds = List.copyOf(cinemaIds);
            showIds = showIds == null ? null : List.copyOf(showIds);
        }
    }

    enum TaskStatus { PENDING, RUNNING, SUCCESS, PARTIAL, FAILED }
}
