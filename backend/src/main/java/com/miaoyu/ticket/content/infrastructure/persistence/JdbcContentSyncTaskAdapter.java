package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSyncTaskPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * V014 同步任务审计的 JDBC 实现。
 *
 * <p>所有条件更新都把状态、持有者和租约写入 WHERE 条件。即使两个应用实例收到同一个管理员请求，
 * 也只有真正取得租约的实例可以调用 Provider 或写入终态。</p>
 */
@Repository
public class JdbcContentSyncTaskAdapter implements ContentSyncTaskPort {
    private static final String PROVIDER = "NETSTART_MAOYAN";
    private static final String RESOURCE_TYPE = "CURRENT_HOT_MOVIES";
    private final JdbcTemplate jdbcTemplate;

    public JdbcContentSyncTaskAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** request_id 在同一 Provider/资源类型下唯一；查询永远不依赖城市编号，避免暴露内部实现。 */
    @Override
    public Optional<SyncTask> findByClientRequestId(String clientRequestId) {
        List<SyncTask> tasks = jdbcTemplate.query("""
                SELECT id, request_id, city_name, provider_city_id, status, started_at, finished_at,
                       success_count, failure_count, failure_category
                  FROM data_sync_log
                 WHERE provider = ? AND resource_type = ? AND request_id = ?
                """, (rs, row) -> new SyncTask(rs.getLong("id"), rs.getString("request_id"),
                rs.getString("city_name"), rs.getString("provider_city_id"),
                SyncTaskStatus.valueOf(rs.getString("status")), time(rs.getTimestamp("started_at")),
                time(rs.getTimestamp("finished_at")), rs.getInt("success_count"), rs.getInt("failure_count"),
                optionalCategory(rs.getString("failure_category"))), PROVIDER, RESOURCE_TYPE, clientRequestId);
        return tasks.stream().findFirst();
    }

    /** PENDING 严格使用 V014 要求的零计数、空错误和空租约形态。 */
    @Override
    public void createPending(SyncTask task) {
        jdbcTemplate.update("""
                INSERT INTO data_sync_log (id, provider, resource_type, request_id, city_name, provider_city_id,
                status, error_code, failure_category, lease_owner, lease_until, total_count, success_count,
                failure_count, started_at, finished_at, error_summary, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, NULL, NULL, NULL, 0, 0, 0, ?, NULL, NULL, 0, ?, ?)
                """, task.syncId(), PROVIDER, RESOURCE_TYPE, task.clientRequestId(), task.cityName(),
                task.providerCityId(), timestamp(task.startedAt()), timestamp(task.startedAt()),
                timestamp(task.startedAt()));
    }

    /** 租约只在 PENDING 上取得一次；V014 尚未收紧 CHECK，但 Writer 已按 V015 目标写入安全形态。 */
    @Override
    public boolean claimPending(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET status = 'RUNNING', lease_owner = ?, lease_until = ?, update_time = ?
                 WHERE id = ? AND status = 'PENDING'
                """, leaseOwner, timestamp(leaseUntil), timestamp(now), syncId) == 1;
    }

    /** 终态同时清空租约，计数关系仍由 V014 CHECK 再次校验。 */
    @Override
    public boolean finish(long syncId, String leaseOwner, SyncTaskStatus status, int totalCount, int successCount,
                          int failureCount, Integer errorCode, FailureCategory failureCategory,
                          LocalDateTime finishedAt) {
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET status = ?, total_count = ?, success_count = ?, failure_count = ?,
                error_code = ?, failure_category = ?, finished_at = ?, lease_owner = NULL, lease_until = NULL,
                update_time = ?
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > ?
                """, status.name(), totalCount, successCount, failureCount, errorCode,
                failureCategory == null ? null : failureCategory.name(), timestamp(finishedAt), timestamp(finishedAt),
                syncId, leaseOwner, timestamp(finishedAt)) == 1;
    }

    /** 每个来源和城市只展示最新一条，保留失败也方便管理员判断是否需要新的请求标识。 */
    @Override
    public List<SourceStatus> findLatestSourceStatuses() {
        return jdbcTemplate.query("""
                SELECT provider, resource_type, city_name, status, started_at, finished_at, success_count,
                       failure_count, failure_category
                  FROM data_sync_log l
                 WHERE id IN (SELECT MAX(id) FROM data_sync_log GROUP BY provider, resource_type, city_name)
                 ORDER BY started_at DESC
                """, (rs, row) -> new SourceStatus(rs.getString("provider"), rs.getString("resource_type"),
                rs.getString("city_name"), SyncTaskStatus.valueOf(rs.getString("status")),
                time(rs.getTimestamp("started_at")), time(rs.getTimestamp("finished_at")),
                rs.getInt("success_count"), rs.getInt("failure_count"),
                optionalCategory(rs.getString("failure_category"))));
    }

    private static Timestamp timestamp(LocalDateTime value) { return Timestamp.valueOf(value); }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static FailureCategory optionalCategory(String value) {
        return value == null ? null : FailureCategory.valueOf(value);
    }
}
