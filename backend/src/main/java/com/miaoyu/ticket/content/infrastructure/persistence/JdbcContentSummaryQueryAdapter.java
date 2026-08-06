package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort.ContentSummaryErrorCode;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.time.Clock;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

/**
 * D 提供给其他模块的内容摘要适配器；调用方只能获得公开摘要，不能接触内容持久化模型。
 *
 * <p>返回值刻意不包含 Entity、地址、经纬度或来源 ID。A 只需 name、area 和时效字段补充展示及支付后
 * 建议，其他内容字段仍留在 D 模块内，避免形成跨模块表访问。</p>
 *
 * <p>删除的影院不参与查询；请求中的未知 ID 不出现在 Map 中，由调用方按自身流程决定返回内容不可用。
 * Adapter 不根据影院名称进行模糊匹配，避免同名影院映射错误。</p>
 *
 * <p>过期判断使用统一业务 Clock，测试可以替换固定时钟。不能使用数据库时间或 JVM 默认时区，否则 A
 * 和 D 在同一时刻可能得到不同的 isExpired 结果。</p>
 */
public class JdbcContentSummaryQueryAdapter implements ContentSummaryQueryPort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /** Clock 只用于计算过期标识，不参与 SQL 条件，保证历史摘要仍可被读取并标记。 */
    /** 适配器只实现公开端口，A 不会获得 JDBC 模板或内容表访问能力。 */
    /** 查询只面向未逻辑删除记录，避免退役影院在支付后建议里再次出现。 */
    /** area 保持数据库原值，行政区名称的展示格式由 D 数据源维护而不是 A 自行拼接。 */
    /** source、dataTime 与过期字段一起返回，调用方可区分 Demo 和后续真实内容。 */
    public JdbcContentSummaryQueryAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 空集合直接返回空 Map，避免生成非法 IN () SQL 或额外访问数据库。 */
    @Override
    public CinemaSummaryBatch findCinemaSummaries(Set<Long> cinemaIds) {
        // 输入 ID 来自 A 的场次查询，仍只作为查询条件，不被写入 D 的内容表。
        if (cinemaIds.isEmpty()) {
            // 没有场次引用影院时不需要查询内容库。
            return new CinemaSummaryBatch(List.of(), Set.of());
        }
        String placeholders = String.join(",", Collections.nCopies(cinemaIds.size(), "?"));
        // 占位符个数严格来自集合大小，所有 ID 仍以参数方式绑定，不能拼入 SQL 文本。
        String sql = """
                SELECT id, name, area, address, source, data_time, expires_at
                 FROM cinema
                 WHERE id IN (%s)
                   AND deleted_at IS NULL
                 ORDER BY id ASC
                """.formatted(placeholders);
        // 只选公开 DTO 所需列，避免把完整 cinema 行泄露给跨模块调用方。
        try {
            List<CinemaSummary> cinemas = jdbcTemplate.query(
                        sql,
                        (resultSet, rowNumber) -> new CinemaSummary(
                                resultSet.getLong("id"),
                                resultSet.getString("name"),
                                resultSet.getString("area"),
                                resultSet.getString("address"),
                                resultSet.getString("source"),
                                resultSet.getTimestamp("data_time").toLocalDateTime(),
                                resultSet.getTimestamp("expires_at") == null
                                        ? null : resultSet.getTimestamp("expires_at").toLocalDateTime(),
                                resultSet.getTimestamp("expires_at") != null
                                        && resultSet.getTimestamp("expires_at").toLocalDateTime().isBefore(
                                        java.time.LocalDateTime.ofInstant(
                                                clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID))),
                    cinemaIds.toArray());
            Set<Long> foundIds = cinemas.stream().map(CinemaSummary::cinemaId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(cinemaIds);
            missingIds.removeAll(foundIds);
            return new CinemaSummaryBatch(cinemas, missingIds);
        } catch (DataAccessException exception) {
            // 数据库异常不是“没有匹配影院”；A 必须得到可重试的内容不可用错误。
            throw new BusinessException(ContentSummaryErrorCode.DATA_UNAVAILABLE);
        }
    }
}
