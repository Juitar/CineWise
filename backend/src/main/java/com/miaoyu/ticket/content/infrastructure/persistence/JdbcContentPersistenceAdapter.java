package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentPersistencePort;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
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
    private final BusinessIdGenerator idGenerator;
    /** H2 测试固定停留在 V009；真实 MySQL 已发布 V014，因此按实际表结构选择兼容写入。 */
    private volatile Boolean v014MovieColumnsAvailable;
    private volatile Boolean v014CinemaColumnsAvailable;
    private volatile Boolean v014SyncLogColumnsAvailable;
    private volatile Boolean identityMappingAvailable;

    /**
     * 只接收 Spring 提供的数据访问对象。
     *
     * <p>不通过本应用 HTTP 调用 Controller，也不借用其他模块的持久层，从构造位置就固定 D 的访问边界。</p>
     */
    @org.springframework.beans.factory.annotation.Autowired
    public JdbcContentPersistenceAdapter(JdbcTemplate jdbcTemplate, BusinessIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    /** 兼容未启用 Spring 的旧测试夹具；正式运行始终使用统一业务 ID 生成器。 */
    JdbcContentPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
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
            updateMovie(existingId, row);
            ensureIdentityMapping(row.source(), "MOVIE", row.sourceMovieId(), existingId, row.dataTime());
            return existingId;
        }
        try {
            insertMovie(row);
            ensureIdentityMapping(row.source(), "MOVIE", row.sourceMovieId(), row.id(), row.dataTime());
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            long existingAfterConflict = requireMovieId(row.source(), row.sourceMovieId(), duplicate);
            updateMovie(existingAfterConflict, row);
            ensureIdentityMapping(row.source(), "MOVIE", row.sourceMovieId(), existingAfterConflict, row.dataTime());
            return existingAfterConflict;
        }
    }

    /** V014 可用时写完整影片资料；旧 H2 骨架只保留 V009 已有列，不能让测试环境误报 SQL 错误。 */
    private void insertMovie(MovieRow row) {
        if (hasV014MovieColumns()) {
            jdbcTemplate.update("""
                    INSERT INTO movie (id, source_movie_id, title, genres_json, duration_minutes, rating,
                    poster_url, summary, release_status, release_date, source_type, source, data_time, expires_at,
                    version, deleted_at, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """, row.id(), row.sourceMovieId(), row.title(), row.genresJson(), row.durationMinutes(),
                    row.rating(), row.posterUrl(), row.summary(), row.releaseStatus(), nullableDate(row.releaseDate()),
                    row.sourceType().name(), row.source(), timestamp(row.dataTime()),
                    nullableTimestamp(row.expiresAt()),
                    timestamp(row.dataTime()), timestamp(row.dataTime()));
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO movie (id, source_movie_id, title, genres_json, duration_minutes, rating,
                source_type, source, data_time, expires_at, version, deleted_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                """, row.id(), row.sourceMovieId(), row.title(), row.genresJson(), row.durationMinutes(),
                row.rating(), row.sourceType().name(), row.source(), timestamp(row.dataTime()),
                nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), timestamp(row.dataTime()));
    }

    /**
     * 用来源身份定位的已有影片只能原地更新，不能新建第二个业务 ID。
     *
     * <p>Provider 有时不会返回海报、简介或上映资料；这些 null 代表本次缺失，不是要求删除旧资料，
     * 所以四个可选字段使用 COALESCE 保留上一份已验证值。</p>
     */
    private void updateMovie(long existingId, MovieRow row) {
        if (!hasV014MovieColumns()) {
            updateLegacyMovie(existingId, row);
            return;
        }
        jdbcTemplate.update("""
                UPDATE movie
                   SET title = ?, genres_json = ?, duration_minutes = ?, rating = ?,
                       poster_url = COALESCE(?, poster_url), summary = COALESCE(?, summary),
                       release_status = COALESCE(?, release_status), release_date = COALESCE(?, release_date),
                       source_type = ?, data_time = ?, expires_at = ?, version = version + 1, update_time = ?
                 WHERE id = ? AND source = ? AND source_movie_id = ?
                """, row.title(), row.genresJson(), row.durationMinutes(), row.rating(), row.posterUrl(),
                row.summary(), row.releaseStatus(), nullableDate(row.releaseDate()), row.sourceType().name(),
                timestamp(row.dataTime()), nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), existingId,
                row.source(), row.sourceMovieId());
    }

    /** V009 兼容路径只更新原有基础字段，供固定 H2 回归测试和短暂旧库过渡使用。 */
    private void updateLegacyMovie(long existingId, MovieRow row) {
        jdbcTemplate.update("""
                UPDATE movie
                   SET title = ?, genres_json = ?, duration_minutes = ?, rating = ?, source_type = ?,
                       data_time = ?, expires_at = ?, version = version + 1, update_time = ?
                 WHERE id = ? AND source = ? AND source_movie_id = ?
                """, row.title(), row.genresJson(), row.durationMinutes(), row.rating(), row.sourceType().name(),
                timestamp(row.dataTime()), nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), existingId,
                row.source(), row.sourceMovieId());
    }

    /**
     * V014 已发布到共享 MySQL，但 H2 测试 profile 按仓库规则仅执行到 V009。
     *
     * <p>检测结果在适配器生命周期内缓存，避免每次同步多做一次探测；缺列只走兼容 SQL，不将缺列当作
     * Provider 失败或吞掉其他数据库异常。</p>
     */
    private boolean hasV014MovieColumns() {
        Boolean cached = v014MovieColumnsAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (v014MovieColumnsAvailable != null) {
                return v014MovieColumnsAvailable;
            }
            try {
                jdbcTemplate.query("SELECT poster_url FROM movie WHERE 1 = 0", (resultSet, rowNumber) -> null);
                v014MovieColumnsAvailable = true;
            } catch (DataAccessException exception) {
                v014MovieColumnsAvailable = false;
            }
            return v014MovieColumnsAvailable;
        }
    }

    /**
     * 影院采用同一恢复方式，保证相同来源 ID 始终返回同一数据库主键。
     *
     * <p>票务种子保存的是这个主键。重复写入若返回新 ID，会造成同一影院的影厅、场次与内容脱节。</p>
     */
    @Override
    public long ensureCinema(CinemaRow row) {
        return ensureCinema(row, null, null);
    }

    /**
     * 影院来自某个受控城市同步时，city_name/provider_city_id 只写入 D 的本地表。
     * 它们不会进入公开 DTO，也不能从前端请求直接传入。
     */
    @Override
    public long ensureCinema(CinemaRow row, String cityName, String cityCode) {
        Long existingId = findCinemaId(row.source(), row.sourceCinemaId());
        if (existingId != null) {
            updateCinema(existingId, row, cityName, cityCode);
            ensureIdentityMapping(row.source(), "CINEMA", row.sourceCinemaId(), existingId, row.dataTime());
            return existingId;
        }
        try {
            insertCinema(row, cityName, cityCode);
            ensureIdentityMapping(row.source(), "CINEMA", row.sourceCinemaId(), row.id(), row.dataTime());
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            long existingAfterConflict = requireCinemaId(row.source(), row.sourceCinemaId(), duplicate);
            updateCinema(existingAfterConflict, row, cityName, cityCode);
            ensureIdentityMapping(row.source(), "CINEMA", row.sourceCinemaId(), existingAfterConflict, row.dataTime());
            return existingAfterConflict;
        }
    }

    private void insertCinema(CinemaRow row, String cityName, String cityCode) {
        if (hasV014CinemaColumns()) {
            jdbcTemplate.update("""
                    INSERT INTO cinema (id, source_cinema_id, name, city_code, city_name, provider_city_id,
                    area, address, longitude, latitude, source_type, source, data_time, expires_at,
                    version, deleted_at, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """, row.id(), row.sourceCinemaId(), row.name(), row.cityCode(), cityName, cityCode,
                    row.area(), row.address(), row.longitude(), row.latitude(), row.sourceType().name(), row.source(),
                    timestamp(row.dataTime()), nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()),
                    timestamp(row.dataTime()));
            return;
        }
        jdbcTemplate.update("""
                    INSERT INTO cinema (id, source_cinema_id, name, city_code, area, address, longitude, latitude,
                    source_type, source, data_time, expires_at, version, deleted_at, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """, row.id(), row.sourceCinemaId(), row.name(), row.cityCode(), row.area(), row.address(),
                    row.longitude(), row.latitude(), row.sourceType().name(), row.source(), timestamp(row.dataTime()),
                    nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), timestamp(row.dataTime()));
    }

    /** 同一来源影院资料发生变化时保留业务 ID，并刷新可展示字段和受控城市归属。 */
    private void updateCinema(long existingId, CinemaRow row, String cityName, String cityCode) {
        if (hasV014CinemaColumns()) {
            jdbcTemplate.update("""
                    UPDATE cinema SET name = ?, city_code = ?, city_name = COALESCE(?, city_name),
                    provider_city_id = COALESCE(?, provider_city_id), area = COALESCE(?, area),
                    address = COALESCE(?, address), longitude = COALESCE(?, longitude),
                    latitude = COALESCE(?, latitude),
                    source_type = ?, data_time = ?, expires_at = ?, version = version + 1, update_time = ?
                    WHERE id = ? AND source = ? AND source_cinema_id = ?
                    """, row.name(), row.cityCode(), cityName, cityCode, row.area(), row.address(),
                    row.longitude(), row.latitude(), row.sourceType().name(), timestamp(row.dataTime()),
                    nullableTimestamp(row.expiresAt()), timestamp(row.dataTime()), existingId, row.source(),
                    row.sourceCinemaId());
            return;
        }
        jdbcTemplate.update("""
                UPDATE cinema SET name = ?, city_code = ?, area = COALESCE(?, area), address = COALESCE(?, address),
                longitude = COALESCE(?, longitude), latitude = COALESCE(?, latitude), source_type = ?,
                data_time = ?, expires_at = ?, version = version + 1, update_time = ?
                WHERE id = ? AND source = ? AND source_cinema_id = ?
                """, row.name(), row.cityCode(), row.area(), row.address(), row.longitude(), row.latitude(),
                row.sourceType().name(), timestamp(row.dataTime()), nullableTimestamp(row.expiresAt()),
                timestamp(row.dataTime()), existingId, row.source(), row.sourceCinemaId());
    }

    private boolean hasV014CinemaColumns() {
        Boolean cached = v014CinemaColumnsAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (v014CinemaColumnsAvailable != null) {
                return v014CinemaColumnsAvailable;
            }
            try {
                jdbcTemplate.query("SELECT city_name FROM cinema WHERE 1 = 0", (resultSet, rowNumber) -> null);
                v014CinemaColumnsAvailable = true;
            } catch (DataAccessException exception) {
                v014CinemaColumnsAvailable = false;
            }
            return v014CinemaColumnsAvailable;
        }
    }

    /** V014 之后把已成功落库的稳定身份同步到映射表；旧 H2 基线没有该表时保留兼容写入。 */
    private void ensureIdentityMapping(String provider, String resourceType, String externalId,
                                       long internalId, LocalDateTime dataTime) {
        if (externalId == null || !"NETSTART_MAOYAN".equals(provider) || !hasIdentityMappingTable()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO content_identity_mapping
                    (id, provider, resource_type, external_id, internal_content_id, status,
                     invalid_reason, invalidated_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', NULL, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE
                    invalidated_at = CASE
                        WHEN status = 'INVALID' THEN invalidated_at
                        WHEN internal_content_id = VALUES(internal_content_id) THEN NULL
                        ELSE VALUES(update_time)
                    END,
                    invalid_reason = CASE
                        WHEN status = 'INVALID' THEN invalid_reason
                        WHEN internal_content_id = VALUES(internal_content_id) THEN NULL
                        ELSE 'IDENTITY_CONFLICT'
                    END,
                    status = CASE
                        WHEN status = 'INVALID' THEN 'INVALID'
                        WHEN internal_content_id = VALUES(internal_content_id) THEN 'ACTIVE'
                        ELSE 'INVALID'
                    END,
                    update_time = VALUES(update_time)
                """, idGenerator == null ? internalId : idGenerator.nextId(), provider, resourceType, externalId,
                internalId,
                timestamp(dataTime), timestamp(dataTime));
    }

    private boolean hasIdentityMappingTable() {
        Boolean cached = identityMappingAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (identityMappingAvailable != null) {
                return identityMappingAvailable;
            }
            try {
                jdbcTemplate.query("SELECT id FROM content_identity_mapping WHERE 1 = 0",
                        (resultSet, rowNumber) -> null);
                identityMappingAvailable = true;
            } catch (DataAccessException exception) {
                identityMappingAvailable = false;
            }
            return identityMappingAvailable;
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
        if (!hasV014SyncLogColumns()) {
            jdbcTemplate.update("""
                    INSERT INTO data_sync_log (id, provider, resource_type, request_id, status, error_code,
                    total_count, success_count, failure_count, started_at, finished_at, error_summary,
                    version, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, row.id(), row.provider(), row.resourceType(), row.requestId(), row.status().name(),
                    row.errorCode(), row.totalCount(), row.successCount(), row.failureCount(),
                    timestamp(row.startedAt()),
                    nullableTimestamp(row.finishedAt()), row.errorSummary(), timestamp(row.startedAt()),
                    timestamp(row.startedAt()));
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO data_sync_log (id, provider, resource_type, request_id, status, error_code,
                total_count, success_count, failure_count, started_at, finished_at, error_summary,
                city_name, provider_city_id, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, row.id(), row.provider(), row.resourceType(), row.requestId(), row.status().name(),
                row.errorCode(), row.totalCount(), row.successCount(), row.failureCount(),
                timestamp(row.startedAt()),
                nullableTimestamp(row.finishedAt()), row.errorSummary(), row.cityName(), row.cityCode(),
                timestamp(row.startedAt()),
                timestamp(row.startedAt()));
    }

    /** V014 前的 H2 回归库没有城市审计列，不能让兼容测试误报 SQL 错误。 */
    private boolean hasV014SyncLogColumns() {
        Boolean cached = v014SyncLogColumnsAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (v014SyncLogColumnsAvailable != null) {
                return v014SyncLogColumnsAvailable;
            }
            try {
                jdbcTemplate.query("SELECT city_name FROM data_sync_log WHERE 1 = 0",
                        (resultSet, rowNumber) -> null);
                v014SyncLogColumnsAvailable = true;
            } catch (DataAccessException exception) {
                v014SyncLogColumnsAvailable = false;
            }
            return v014SyncLogColumnsAvailable;
        }
    }

    /**
     * 用本次目录给出的身份批量判断已完成影片。
     *
     * <p>SQL 参数始终由调用方生成占位符，外部身份不拼接进 SQL。空目录直接返回空集合，
     * 这样 Provider 未给出候选时不会构造无意义的全表查询。</p>
     */
    @Override
    public Set<String> findExistingMovieSourceIds(String source) {
        return Set.copyOf(jdbcTemplate.query("SELECT source_movie_id FROM movie WHERE source = ? "
                        + "AND source_movie_id IS NOT NULL AND deleted_at IS NULL",
                (resultSet, rowNumber) -> resultSet.getString("source_movie_id"), source));
    }

    @Override
    public Map<String, MovieState> findExistingMovieStates(String source) {
        if (!hasV014MovieColumns()) {
            return Map.of();
        }
        return jdbcTemplate.query("""
                SELECT source_movie_id, release_date, release_status, data_time
                  FROM movie
                 WHERE source = ? AND source_movie_id IS NOT NULL AND deleted_at IS NULL
                """, (resultSet, rowNumber) -> Map.entry(resultSet.getString("source_movie_id"),
                new MovieState(resultSet.getDate("release_date") == null ? null
                        : resultSet.getDate("release_date").toLocalDate().toString(),
                        resultSet.getString("release_status"),
                        resultSet.getTimestamp("data_time") == null ? null
                                : resultSet.getTimestamp("data_time").toLocalDateTime())), source)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 同来源与来源 ID 的查询只用于明确非空身份的内容，返回 null 代表还需首次插入。
     *
     * <p>固定种子走独立的 ContentSeedRepository，不经过此适配器。这里的同来源更新只处理已经通过
     * Provider 字段校验的真实资料，避免同步后快照已变而 movie 表仍停留在旧资料。</p>
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

    /** V014 的上映日期是 SQL DATE；空值必须以 JDBC null 写入，不能用同步时间替代。 */
    private Date nullableDate(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
