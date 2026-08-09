package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.content.application.LiveDemoPurchaseCatalogQueryPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 只读取得 A 初始化本地演示排期所需的真实内容引用。
 *
 * <p>这里刻意不复用普通内容查询的 Demo 回退：A 需要判断是否存在可展示的真实目录，
 * 把固定 Demo 冒充真实目录会让后续的 Mock 排期错误关联到真实影院。</p>
 */
@Repository
public class JdbcLiveDemoPurchaseCatalogAdapter implements LiveDemoPurchaseCatalogQueryPort {

    /** 目录不接受快照或 Mock，避免 A 将演示资料关联到真实影院。 */
    private static final String LIVE = "LIVE";
    /** A 已确认本期只能使用一个实际来源，不能把多来源悄悄合并。 */
    private static final String NETSTART_MAOYAN = "NETSTART_MAOYAN";
    /** 影片只需覆盖固定演示模板；影院必须完整返回，不能套用这个上限。 */
    private static final int MOVIE_LIMIT = 3;
    /** 每日内容同步在整个同步周期内都可作为本地演示排期引用，不能在上午提前失效。 */
    private static final int DEMO_REFERENCE_VALIDITY_HOURS = 24;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcLiveDemoPurchaseCatalogAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public ContentPurchaseQueryPort.DemoPurchaseCatalog findLiveCatalog(String cityCode) {
        // 只使用注入时钟判断资料有效期，避免机器时区影响 A 是否写入 Mock 排期。
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        try {
            // 影院按城市完整读取；数据库 ID 已唯一，排序同时保证调用结果稳定。
            List<CinemaRow> cinemaRows = jdbcTemplate.query("""
                    SELECT id, source_cinema_id, data_time, expires_at
                      FROM cinema
                     WHERE city_code = ? AND source_type = ? AND source = ? AND deleted_at IS NULL
                       AND id > 0 AND source_cinema_id IS NOT NULL AND TRIM(source_cinema_id) <> ''
                     ORDER BY id ASC
                    """, (rs, rowNum) -> new CinemaRow(new ContentPurchaseQueryPort.CinemaRef(
                    rs.getLong("id"), rs.getString("source_cinema_id")),
                    rs.getTimestamp("data_time").toLocalDateTime(),
                    rs.getTimestamp("expires_at").toLocalDateTime()), cityCode, LIVE, NETSTART_MAOYAN)
                    .stream().filter(row -> isCurrentDemoReference(row.dataAt(), now)).toList();
            // 影片独立取前三部，避免某个影院数量大时意外扩大 A 的演示种子规模。
            List<MovieRow> movieRows = jdbcTemplate.query("""
                    SELECT id, source_movie_id, duration_minutes, data_time, expires_at
                      FROM movie
                     WHERE source_type = ? AND source = ? AND deleted_at IS NULL
                       AND id > 0 AND source_movie_id IS NOT NULL AND TRIM(source_movie_id) <> ''
                       AND duration_minutes > 0
                     ORDER BY id ASC
                    """, (rs, rowNum) -> new MovieRow(new ContentPurchaseQueryPort.MovieRef(
                    rs.getLong("id"), rs.getString("source_movie_id"), rs.getInt("duration_minutes")),
                    rs.getTimestamp("data_time").toLocalDateTime(),
                    rs.getTimestamp("expires_at").toLocalDateTime()),
                    LIVE, NETSTART_MAOYAN).stream().filter(row -> isCurrentDemoReference(row.dataAt(), now))
                    .limit(MOVIE_LIMIT).toList();
            if (cinemaRows.isEmpty() || movieRows.isEmpty()) {
                // 任何一侧缺失都不能生成半套票务演示资料；当前时间只是本次空目录的检查时间。
                return new ContentPurchaseQueryPort.DemoPurchaseCatalog(List.of(), List.of(), NETSTART_MAOYAN,
                        now.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                        now.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime());
            }
            // 目录时效按最早记录收敛，A 在该时间后不会继续把其中的旧资料当作有效资料。
            List<LocalDateTime> dataTimes = java.util.stream.Stream.concat(movieRows.stream().map(MovieRow::dataAt),
                    cinemaRows.stream().map(CinemaRow::dataAt)).toList();
            LocalDateTime dataAt = dataTimes.stream().min(LocalDateTime::compareTo).orElseThrow();
            LocalDateTime expiresAt = dataTimes.stream().map(this::effectiveDemoReferenceExpiry)
                    .min(LocalDateTime::compareTo).orElseThrow();
            return new ContentPurchaseQueryPort.DemoPurchaseCatalog(movieRows.stream().map(MovieRow::ref).toList(),
                    cinemaRows.stream().map(CinemaRow::ref).toList(), NETSTART_MAOYAN,
                    offset(dataAt), offset(expiresAt));
        } catch (DataAccessException exception) {
            // 存储故障和“该城市没有真实目录”不同，A 需要据此停止而非继续写 Mock。
            throw new BusinessException(ContentSummaryQueryPort.ContentSummaryErrorCode.DATA_UNAVAILABLE);
        }
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    /**
     * 影院/影片页面仍展示原始 expiresAt；这里只决定它能否在当天被 A 用作本地 Mock 的稳定引用。
     */
    private boolean isCurrentDemoReference(LocalDateTime dataAt, LocalDateTime now) {
        return effectiveDemoReferenceExpiry(dataAt).isAfter(now);
    }

    private LocalDateTime effectiveDemoReferenceExpiry(LocalDateTime dataAt) {
        return dataAt.plusHours(DEMO_REFERENCE_VALIDITY_HOURS);
    }

    private record MovieRow(ContentPurchaseQueryPort.MovieRef ref, LocalDateTime dataAt, LocalDateTime expiresAt) { }

    private record CinemaRow(ContentPurchaseQueryPort.CinemaRef ref, LocalDateTime dataAt, LocalDateTime expiresAt) { }
}
