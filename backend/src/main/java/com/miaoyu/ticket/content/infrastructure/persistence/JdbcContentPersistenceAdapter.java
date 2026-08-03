package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentPersistencePort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * `movie`、`cinema`、快照和同步日志的 JDBC 适配器。
 *
 * <p>这个类属于 D 的 content 模块，不调用 A 的 Mapper。影片和影院的“先查再插入、冲突后重查”
 * 处理并发重复初始化；快照和日志不吞掉唯一键冲突，调用方必须按原 requestId 查询并恢复。</p>
 */
@Repository
public class JdbcContentPersistenceAdapter implements ContentPersistencePort {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 只接收 Spring 提供的数据访问对象。
     *
     * <p>不通过本应用 HTTP 调用 Controller，也不借用其他模块的持久层，从构造位置就固定 D 的访问边界。</p>
     */
    public JdbcContentPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 来源 ID 可空的未来 Provider 没有身份规则前不得走此方法。
     *
     * <p>MySQL 的联合唯一键允许多个 NULL；如果直接写入会把不同影片误判成同一个或让重复数据漏过。
     * 真实 Provider 接入时必须先补它自己的身份识别方案。</p>
     */
    @Override
    public long ensureMovie(MovieRow row) {
        Long existingId = findMovieId(row.source(), row.sourceMovieId());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO movie (id, source_movie_id, title, genres_json, duration_minutes, rating,
                    source_type, source, data_time, expires_at, version, deleted_at, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """, row.id(), row.sourceMovieId(), row.title(), row.genresJson(), row.durationMinutes(),
                    row.rating(), row.sourceType().name(), row.source(), timestamp(row.dataTime()),
                    nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), timestamp(row.dataTime()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            return requireMovieId(row.source(), row.sourceMovieId(), duplicate);
        }
    }

    /**
     * 影院采用同一恢复方式，保证相同来源 ID 始终返回同一数据库主键。
     *
     * <p>票务种子保存的是这个主键。重复写入若返回新 ID，会造成同一影院的影厅、场次与内容脱节。</p>
     */
    @Override
    public long ensureCinema(CinemaRow row) {
        Long existingId = findCinemaId(row.source(), row.sourceCinemaId());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO cinema (id, source_cinema_id, name, city_code, area, address, longitude, latitude,
                    source_type, source, data_time, expires_at, version, deleted_at, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """, row.id(), row.sourceCinemaId(), row.name(), row.cityCode(), row.area(), row.address(),
                    row.longitude(), row.latitude(), row.sourceType().name(), row.source(), timestamp(row.dataTime()),
                    nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), timestamp(row.dataTime()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            return requireCinemaId(row.source(), row.sourceCinemaId(), duplicate);
        }
    }

    /**
     * 快照保留原始时间；过期时间先后关系由 V004 的 CHECK 再次校验。
     *
     * <p>应用层未来可以根据这两个时间判断是否允许只读展示，但不能在这里把过期快照改成新的实时内容。</p>
     */
    @Override
    public void insertSnapshot(SnapshotRow row) {
        jdbcTemplate.update("""
                INSERT INTO external_data_snapshot (id, provider, external_id, data_type, payload_json,
                data_time, expire_time, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, row.id(), row.provider(), row.externalId(), row.dataType(), row.payloadJson(),
                timestamp(row.dataTime()), nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()),
                timestamp(row.dataTime()));
    }

    /**
     * 同步状态枚举和值域检查留给数据库执行，适配器不在失败时伪造成功记录。
     *
     * <p>数据库 CHECK 能保护所有写入入口；即使后续 Job 或手工修复绕过 Application Service，错误统计也不能落库。</p>
     */
    @Override
    public void insertSyncLog(SyncLogRow row) {
        jdbcTemplate.update("""
                INSERT INTO data_sync_log (id, provider, resource_type, request_id, status, error_code,
                total_count, success_count, failure_count, started_at, finished_at, error_summary,
                version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, row.id(), row.provider(), row.resourceType(), row.requestId(), row.status().name(),
                row.errorCode(), row.totalCount(), row.successCount(), row.failureCount(), timestamp(row.startedAt()),
                nullableTimestamp(row.finishedAt()), row.errorSummary(), timestamp(row.startedAt()),
                timestamp(row.startedAt()));
    }

    /**
     * 同来源与来源 ID 的查询只用于明确非空身份的内容，返回 null 代表还需首次插入。
     *
     * <p>这里不对已存在记录更新标题或评分，防止固定种子在演示启动时覆盖真实 Provider 后续写入的内容。</p>
     */
    private Long findMovieId(String source, String sourceMovieId) {
        return findId("SELECT id FROM movie WHERE source = ? AND source_movie_id = ?", source, sourceMovieId);
    }

    /**
     * 影院也只以已确认的来源身份查找，不按名称或地址做可能误合并的模糊匹配。
     *
     * <p>同名影院可能处于不同城市；地址格式变化也不能成为覆盖既有场次关联的理由。</p>
     */
    private Long findCinemaId(String source, String sourceCinemaId) {
        return findId("SELECT id FROM cinema WHERE source = ? AND source_cinema_id = ?", source, sourceCinemaId);
    }

    /** 查询结果最多一条由表唯一键保证；空列表不抛异常，交给调用方决定是否插入。 */
    private Long findId(String sql, String source, String sourceId) {
        List<Long> ids = jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getLong("id"), source, sourceId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /**
     * 并发首次写入导致的唯一键冲突不算初始化失败。
     *
     * <p>只有冲突后仍查不到记录时才向上抛出原异常，避免掩盖非预期约束或事务回滚问题。</p>
     */
    private long requireMovieId(String source, String sourceMovieId, DuplicateKeyException cause) {
        Long id = findMovieId(source, sourceMovieId);
        if (id == null) {
            throw cause;
        }
        return id;
    }

    /** 与影片的并发恢复原则相同，确保影院主键不会因竞争而改变。 */
    private long requireCinemaId(String source, String sourceCinemaId, DuplicateKeyException cause) {
        Long id = findCinemaId(source, sourceCinemaId);
        if (id == null) {
            throw cause;
        }
        return id;
    }

    /** 所有表统一使用 DATETIME(3)，避免 JDBC 默认时区把本地业务时间改写为另一时刻。 */
    private Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    /** 只有 expiresAt 和 finishedAt 允许为空；其余审计时间由调用方明确提供。 */
    private Timestamp nullableTimestamp(LocalDateTime value) {
        return value == null ? null : timestamp(value);
    }
}
