package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/** 临时内容摘要适配器；调用方只能获得公开摘要，不能接触内容持久化模型。 */
public class JdbcContentSummaryQueryAdapter implements ContentSummaryQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentSummaryQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<Long, CinemaSummary> findCinemaSummaries(Set<Long> cinemaIds) {
        if (cinemaIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(cinemaIds.size(), "?"));
        String sql = """
                SELECT id, name, source, data_time
                  FROM cinema
                 WHERE id IN (%s)
                   AND deleted_at IS NULL
                """.formatted(placeholders);
        return jdbcTemplate.query(
                        sql,
                        (resultSet, rowNumber) -> new CinemaSummary(
                                resultSet.getLong("id"),
                                resultSet.getString("name"),
                                resultSet.getString("source"),
                                resultSet.getTimestamp("data_time").toLocalDateTime()),
                        cinemaIds.toArray())
                .stream()
                .collect(Collectors.toUnmodifiableMap(CinemaSummary::cinemaId, Function.identity()));
    }
}
