package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentLocalMovieCatalogPort;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 从本地 movie 表读取完整影片目录，按上映日期倒序和业务 ID 升序返回。
 * V014 字段不存在时只兼容读取旧基础列，不伪造上映日期、海报或简介。
 */
@Repository
public class JdbcContentLocalMovieCatalogAdapter implements ContentLocalMovieCatalogPort {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LIVE_SOURCE_TYPE = ContentSourceType.LIVE.name();
    private static final String NETSTART_SOURCE = "NETSTART_MAOYAN";
    /** 字段探测只做一次，避免每个影片列表请求额外访问数据库元数据。 */
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private volatile Boolean v014Available;

    public JdbcContentLocalMovieCatalogAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        // Clock 注入保证过期判断可复现，不依赖服务器当前默认时间。
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> findMovies(String keyword, String releaseStatus) {
        // V014 字段存在才把 movie 表作为完整目录，避免旧库缺列时返回半真半假的筛选结果。
        boolean extended = hasV014Columns();
        // V009 旧测试库没有完整目录字段，不能把旧种子误报成真实本地目录；交给原有回退顺序处理。
        if (!extended) {
            // Optional.empty 交给 ContentQueryService 按既有快照和 Demo 顺序处理。
            return Optional.empty();
        }
        // 两个筛选条件都使用参数绑定，关键字只能影响值，不能改变 SQL 结构。
        String optional = ", poster_url, summary, release_date, release_status";
        String statusClause = releaseStatus != null ? " AND release_status = ?" : "";
        // 关键字为空时不追加 LIKE，避免空串意外匹配整张表并掩盖调用方问题。
        String keywordClause = keyword == null ? "" : " AND LOWER(title) LIKE LOWER(?)";
        // 排序先按上映日期，再按内部 ID，页面翻页时不会因数据库自然顺序变化跳项。
        String sql = """
                SELECT id, source_movie_id, title, genres_json, duration_minutes, rating,
                       source_type, source, data_time, expires_at%s
                  FROM movie
                 WHERE source_type = ? AND source = ?
                   AND source_movie_id IS NOT NULL AND deleted_at IS NULL
                   AND duration_minutes > 0 AND rating IS NOT NULL%s%s
                 ORDER BY %s
                """.formatted(optional, statusClause, keywordClause,
                "release_date DESC, id ASC");
        // 参数添加顺序与 SQL 中状态、关键字占位符的顺序一致。
        List<Object> args = new ArrayList<>();
        // 列表只代表完整的真实影片目录；Demo 必须由上层整体回退，不能与 LIVE 行混在同一响应中。
        args.add(LIVE_SOURCE_TYPE);
        args.add(NETSTART_SOURCE);
        if (releaseStatus != null) {
            // 上层已限制枚举值，此处仍坚持参数绑定。
            args.add(releaseStatus);
        }
        if (keyword != null) {
            // LIKE 只拼接通配符，不拼接 SQL 片段。
            args.add("%" + keyword + "%");
        }
        List<Row> rows = jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            // DATE 可为空；来源没有上映日期时保持空值，绝不替换成同步时间。
            Date releaseDate = resultSet.getDate("release_date");
            return new Row(new MovieContent(resultSet.getLong("id"), resultSet.getString("source_movie_id"),
                    resultSet.getString("title"), resultSet.getString("genres_json"),
                    resultSet.getInt("duration_minutes"), resultSet.getBigDecimal("rating"),
                    resultSet.getString("poster_url"),
                    resultSet.getString("summary"),
                    releaseDate == null ? null : releaseDate.toLocalDate().toString(),
                    resultSet.getString("release_status")),
                    resultSet.getString("source"), resultSet.getString("source_type"),
                    resultSet.getTimestamp("data_time").toLocalDateTime(),
                    resultSet.getTimestamp("expires_at") == null ? null
                            : resultSet.getTimestamp("expires_at").toLocalDateTime());
        }, args.toArray());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
        // 时区固定为中国业务日期，和凌晨同步调度使用同一标准。
        // 列表的资料时间取本批最新值，只描述内容资料的新旧，不承诺票务实时性。
        LocalDateTime dataTime = rows.stream().map(Row::dataTime).max(LocalDateTime::compareTo).orElse(now);
        // 到期时间同样来自已落库资料；空目录不伪造一份 LIVE 资料时间。
        LocalDateTime expiresAt = rows.stream().map(Row::expiresAt).filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(dataTime);
        // 来源沿用第一条真实资料；无数据时明确标成 LOCAL_CATALOG 快照而非 Provider 响应。
        ContentSource source = rows.stream().findFirst()
                .map(row -> new ContentSource(row.source(), parseSourceType(row.sourceType())))
                .orElse(new ContentSource("LOCAL_CATALOG", ContentSourceType.SNAPSHOT));
        // ContentResult 保留统一来源和过期标识，Controller 无需读取数据库实现细节。
        List<MovieContent> movies = rows.stream().map(Row::movie).toList();
        // 空列表仍返回成功内容结果，页面可显示“当前没有本地完整目录”。
        return Optional.of(new ContentResult<>(movies, source, dataTime, expiresAt,
                expiresAt.isBefore(now), false, null));
    }

    private ContentSourceType parseSourceType(String value) {
        // 枚举扩展或历史脏值不能阻塞基础资料浏览。
        // 历史脏值只降为 SNAPSHOT，不能让一次读取异常导致整个目录不可用。
        try {
            return ContentSourceType.valueOf(value);
        } catch (RuntimeException exception) {
            // SNAPSHOT 只说明本地读取，不代表 Provider 当前正常。
            return ContentSourceType.SNAPSHOT;
        }
    }

    private boolean hasV014Columns() {
        // 这是兼容 V009/H2 测试基线的过渡判断，不替代真实 MySQL 的 V014 验证。
        // 结果缓存到 Bean 内；迁移能力在运行期间不会反复探测数据库字段。
        if (v014Available != null) {
            // volatile 保证并发请求共享同一探测结论。
            return v014Available;
        }
        try {
            // 空结果查询只验证字段存在，不读取或记录任何影片资料。
            jdbcTemplate.query("SELECT poster_url FROM movie WHERE 1 = 0", (resultSet, rowNumber) -> null);
            v014Available = true;
        } catch (DataAccessException exception) {
            // 旧 H2 基线没有这些列时走既有回退，不把异常暴露给普通用户请求。
            v014Available = false;
        }
        return v014Available;
    }

    /** 列表响应的临时聚合对象，来源和时效字段只在适配器内使用。 */
    private record Row(MovieContent movie, String source, String sourceType,
                       LocalDateTime dataTime, LocalDateTime expiresAt) { }
}
