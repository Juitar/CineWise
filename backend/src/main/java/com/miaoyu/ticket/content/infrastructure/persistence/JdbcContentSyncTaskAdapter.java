package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSyncTaskPort;
import com.miaoyu.ticket.content.application.SyncTaskStateValidator;
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
        // 查询任务时可以读内部城市编号供 Application 层继续处理，但不会返回到 Controller。
        // 唯一键由 provider/resourceType/requestId 组成，不能按城市或最近记录做模糊恢复。
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
        // 在发出任何 Provider 请求前校验，保证非法任务不会占用幂等请求标识。
        SyncTaskStateValidator.validatePending(task);
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
        // 只有 PENDING 可以取得租约，防止同一请求的第二个实例再次调用 Provider。
        SyncTaskStateValidator.validateRunning(leaseOwner, leaseUntil, now);
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET status = 'RUNNING', lease_owner = ?, lease_until = ?, update_time = ?
                 WHERE id = ? AND status = 'PENDING'
                """, leaseOwner, timestamp(leaseUntil), timestamp(now), syncId) == 1;
    }

    /**
     * 续租必须同时命中持有者和未过期租约。
     *
     * <p>返回零行说明任务已被恢复器收敛、被其他实例接管或租约已经到期。调用方必须丢弃后续响应，
     * 不能以“网络请求先开始”为由覆盖较新的任务结果。</p>
     */
    @Override
    public boolean renewLease(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
        // 先在 Java 层拒绝空持有者或倒退时间，SQL 再检查数据库中的实际租约。
        SyncTaskStateValidator.validateRunning(leaseOwner, leaseUntil, now);
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET lease_until = ?, update_time = ?
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > ?
                """, timestamp(leaseUntil), timestamp(now), syncId, leaseOwner, timestamp(now)) == 1;
    }

    /**
     * 条件 UPDATE 成功后由 InnoDB 持有排他行锁至外层资料事务结束。
     *
     * <p>恢复器的过期更新会等待这把锁；提交后它看到的是刚续期的租约，回滚后才可把真正过期任务收敛为失败。</p>
     */
    @Override
    public boolean lockAndRenewActiveLease(long syncId, String leaseOwner, LocalDateTime leaseUntil,
                                           LocalDateTime now) {
        // 此入口与普通续租使用同一条件，区别仅在于外层内容事务会持有行锁直到提交。
        SyncTaskStateValidator.validateRunning(leaseOwner, leaseUntil, now);
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET lease_until = ?, update_time = ?
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > ?
                """, timestamp(leaseUntil), timestamp(now), syncId, leaseOwner, timestamp(now)) == 1;
    }

    /** 终态同时清空租约，计数关系仍由 V014 CHECK 再次校验。 */
    @Override
    public boolean finish(long syncId, String leaseOwner, SyncTaskStatus status, int totalCount, int successCount,
                          int failureCount, Integer errorCode, FailureCategory failureCategory,
                          LocalDateTime finishedAt) {
        // 终态必须带发起该次条件更新的持有者；SQL 负责拒绝已过期或被接管的持有者。
        SyncTaskStateValidator.validateLeaseOwner(leaseOwner);
        // V014 尚未用 CHECK 限死五种状态，因此先由 Writer 阻止矛盾组合进入历史表。
        SyncTaskStateValidator.validateTerminal(status, totalCount, successCount, failureCount, errorCode,
                failureCategory, finishedAt);
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET status = ?, total_count = ?, success_count = ?, failure_count = ?,
                error_code = ?, failure_category = ?, finished_at = ?, lease_owner = NULL, lease_until = NULL,
                update_time = ?
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > ?
                """, status.name(), totalCount, successCount, failureCount, errorCode,
                failureCategory == null ? null : failureCategory.name(), timestamp(finishedAt), timestamp(finishedAt),
                syncId, leaseOwner, timestamp(finishedAt)) == 1;
    }

    /**
     * 进程退出后不会有持有者续租，恢复器只在租约确实过期时写固定 INTERNAL 失败。
     *
     * <p>该更新不重新访问 Provider；相同 clientRequestId 后续只能查询这个终态，避免管理员因结果未知
     * 反复产生外部调用。V014 允许零计数 FAILED，满足“尚未取得候选即失败”的兼容形态。</p>
     */
    @Override
    public int failExpiredRunningTasks(LocalDateTime now, int errorCode, FailureCategory failureCategory) {
        // 恢复器只有在数据库确认租约已到期时才写失败，绝不重放未知结果的外部请求。
        // 固定失败分类避免把连接异常、Provider 响应或调用参数留进审计表。
        return jdbcTemplate.update("""
                UPDATE data_sync_log SET status = 'FAILED', error_code = ?, failure_category = ?,
                finished_at = ?, lease_owner = NULL, lease_until = NULL, update_time = ?
                 WHERE provider = ? AND resource_type = ? AND status = 'RUNNING' AND lease_until < ?
                """, errorCode, failureCategory.name(), timestamp(now), timestamp(now), PROVIDER, RESOURCE_TYPE,
                timestamp(now));
    }

    /** 每个来源和城市只展示最新一条，保留失败也方便管理员判断是否需要新的请求标识。 */
    @Override
    public List<SourceStatus> findLatestSourceStatuses() {
        // 每个城市显示最近任务，但最近成功时间单独计算，避免一次失败覆盖管理员的成功依据。
        // 快照只聚合来源时间，不返回 payload_json 内的任何第三方字段。
        return jdbcTemplate.query("""
                SELECT l.provider, l.resource_type, l.city_name, l.status, l.started_at, l.finished_at,
                       l.success_count, l.failure_count, l.failure_category,
                       (SELECT MAX(s.finished_at) FROM data_sync_log s
                         WHERE s.provider = l.provider AND s.resource_type = l.resource_type
                           AND s.city_name <=> l.city_name AND s.status = 'SUCCESS') AS last_success_at,
                       snapshot.data_time, snapshot.expire_time
                  FROM data_sync_log l
                  LEFT JOIN (
                    SELECT MAX(data_time) AS data_time, MAX(expire_time) AS expire_time
                      FROM external_data_snapshot
                     WHERE provider = 'CONTENT_SNAPSHOT' AND data_type = 'MOVIE'
                       AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.source.name')) = 'NETSTART_MAOYAN'
                  ) snapshot ON 1 = 1
                 WHERE id IN (SELECT MAX(id) FROM data_sync_log GROUP BY provider, resource_type, city_name)
                 ORDER BY started_at DESC
                """, (rs, row) -> new SourceStatus(rs.getString("provider"), rs.getString("resource_type"),
                rs.getString("city_name"), SyncTaskStatus.valueOf(rs.getString("status")),
                time(rs.getTimestamp("started_at")), time(rs.getTimestamp("finished_at")),
                time(rs.getTimestamp("last_success_at")), rs.getInt("success_count"), rs.getInt("failure_count"),
                optionalCategory(rs.getString("failure_category")), time(rs.getTimestamp("data_time")),
                time(rs.getTimestamp("expire_time"))));
    }

    /** JDBC 时间转换集中在此处，所有业务时间已由调用方按 Asia/Shanghai 生成。 */
    private static Timestamp timestamp(LocalDateTime value) { return Timestamp.valueOf(value); }
    /** NULL 时间保留为 NULL，避免把未完成任务伪造成已经结束。 */
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    /** 枚举解析只接受库内固定值；迁移或人工数据损坏应显式失败，不能静默当作无失败分类。 */
    private static FailureCategory optionalCategory(String value) {
        // NULL 只表示成功或尚未完成，不会被转换成一个看似合理的失败类型。
        return value == null ? null : FailureCategory.valueOf(value);
    }
}
