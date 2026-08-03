package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentIdentityLookupPort;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 从 D 负责的内容表把实际数据库主键映射回固定 Demo 来源 ID。
 *
 * <p>查询只读取 movie/cinema 的来源 ID，不向 Provider 暴露完整行，也不访问 A 的票务表。固定种子在
 * 数据库中必须沿用 `demo-seed` 业务键；`DEMO_CONTENT` 只用于 JSON 与返回结果来源。已逻辑删除或不是
 * `demo-seed` 的内容不能作为固定目录回退，避免把其他 Provider 的数据库 ID 错误映射。</p>
 */
@Repository
public class JdbcContentIdentityLookupAdapter implements ContentIdentityLookupPort {

    private static final String DEMO_SEED_SOURCE = "demo-seed";

    private final JdbcTemplate jdbcTemplate;

    /** 适配器只依赖 D 内容表；Provider 通过 Application 端口取得最小映射信息。 */
    public JdbcContentIdentityLookupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 根据资源类型选择固定列名，列名来自受控枚举而非外部输入，查询值仍使用参数绑定。 */
    @Override
    public Optional<String> findSourceId(ContentResourceType resourceType, long contentId) {
        String table = resourceType == ContentResourceType.MOVIE ? "movie" : "cinema";
        String sourceIdColumn = resourceType == ContentResourceType.MOVIE ? "source_movie_id" : "source_cinema_id";
        List<String> sourceIds = jdbcTemplate.query("""
                SELECT %s FROM %s
                 WHERE id = ?
                   AND source = ?
                   AND deleted_at IS NULL
                """.formatted(sourceIdColumn, table),
                (resultSet, rowNumber) -> resultSet.getString(sourceIdColumn), contentId, DEMO_SEED_SOURCE);
        return sourceIds.stream().findFirst();
    }
}
