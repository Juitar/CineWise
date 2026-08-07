package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeImportTaskRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** V021 异步导入任务 JDBC 适配器；所有状态写入均使用租约与版本条件。 */
@Repository
public class JdbcExternalShowtimeImportTaskRepository implements ExternalShowtimeImportTaskRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcExternalShowtimeImportTaskRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void create(Task task) {
        jdbc.update("""
                INSERT INTO ticketing_external_showtime_import_task
                (id, task_id, client_request_id, show_date, cinema_ids, status, total_count, success_count,
                 failure_count, truncated, imported_show_ids, error_code, started_at, finished_at, expire_at,
                 version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 0, 0, 0, NULL, NULL, NULL, NULL, ?, 0, ?, ?)
                """, task.id(), task.taskId(), task.clientRequestId(), task.showDate(), json(task.cinemaIds()),
                Timestamp.valueOf(task.expireAt()), Timestamp.valueOf(task.createdAt()),
                Timestamp.valueOf(task.updatedAt()));
    }

    @Override
    public Optional<Task> findByTaskId(String taskId) { return find("task_id = ?", taskId); }

    @Override
    public Optional<Task> findByClientRequestId(String clientRequestId) {
        return find("client_request_id = ?", clientRequestId);
    }

    @Override
    public Optional<Task> findNextPending() {
        return find("status = 'PENDING' ORDER BY create_time LIMIT 1");
    }

    @Override
    public boolean claim(String taskId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
        return jdbc.update("""
                UPDATE ticketing_external_showtime_import_task
                   SET status = 'RUNNING', lease_owner = ?, lease_until = ?, started_at = ?, version = version + 1,
                       update_time = ?
                 WHERE task_id = ? AND status = 'PENDING' AND lease_owner IS NULL
                """, leaseOwner, timestamp(leaseUntil), timestamp(now), timestamp(now), taskId) == 1;
    }

    @Override
    public boolean finish(String taskId, String leaseOwner, long version, TaskStatus status, int totalCount,
            int successCount, int failureCount, boolean truncated, List<Long> showIds, Integer errorCode,
            LocalDateTime finishedAt) {
        return jdbc.update("""
                UPDATE ticketing_external_showtime_import_task
                   SET status = ?, total_count = ?, success_count = ?, failure_count = ?, truncated = ?,
                       imported_show_ids = ?, error_code = ?, finished_at = ?, lease_owner = NULL,
                       lease_until = NULL, version = version + 1, update_time = ?
                 WHERE task_id = ? AND status = 'RUNNING' AND lease_owner = ? AND version = ?
                """, status.name(), totalCount, successCount, failureCount, truncated ? 1 : 0,
                showIds == null ? null : json(showIds), errorCode, timestamp(finishedAt), timestamp(finishedAt),
                taskId, leaseOwner, version) == 1;
    }

    @Override
    public int requeueExpired(LocalDateTime now) {
        return jdbc.update("""
                UPDATE ticketing_external_showtime_import_task
                   SET status = 'PENDING', started_at = NULL, error_code = NULL, finished_at = NULL,
                       total_count = 0, success_count = 0, failure_count = 0, truncated = 0,
                       imported_show_ids = NULL, lease_owner = NULL,
                       lease_until = NULL, version = version + 1, update_time = ?
                 WHERE status = 'RUNNING' AND lease_until < ?
                """, timestamp(now), timestamp(now));
    }

    private Optional<Task> find(String where, Object... args) {
        List<Task> tasks = jdbc.query("""
                SELECT id, task_id, client_request_id, show_date, cinema_ids, status, lease_owner, lease_until,
                       total_count, success_count, failure_count, truncated, imported_show_ids, error_code,
                       started_at, finished_at, expire_at, version, create_time, update_time
                  FROM ticketing_external_showtime_import_task WHERE
                """ + " " + where,
                (rs, ignored) -> new Task(rs.getLong("id"), rs.getString("task_id"),
                        rs.getString("client_request_id"), rs.getObject("show_date", LocalDate.class),
                        readLongs(rs.getString("cinema_ids")),
                        TaskStatus.valueOf(rs.getString("status")), rs.getString("lease_owner"),
                        time(rs.getTimestamp("lease_until")), rs.getInt("total_count"), rs.getInt("success_count"),
                        rs.getInt("failure_count"), rs.getBoolean("truncated"),
                        rs.getString("imported_show_ids") == null ? null : readLongs(rs.getString("imported_show_ids")),
                        (Integer) rs.getObject("error_code"), time(rs.getTimestamp("started_at")),
                        time(rs.getTimestamp("finished_at")), time(rs.getTimestamp("expire_at")), rs.getLong("version"),
                        time(rs.getTimestamp("create_time")), time(rs.getTimestamp("update_time"))),
                args);
        return tasks.stream().findFirst();
    }

    private String json(List<Long> values) {
        try {
            return objectMapper.writeValueAsString(values);
        }
        catch (JsonProcessingException exception) { throw new IllegalStateException("任务结果序列化失败", exception); }
    }

    private List<Long> readLongs(String value) {
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        }
        catch (JsonProcessingException exception) { throw new IllegalStateException("任务 JSON 损坏", exception); }
    }

    private static Timestamp timestamp(LocalDateTime value) { return Timestamp.valueOf(value); }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
