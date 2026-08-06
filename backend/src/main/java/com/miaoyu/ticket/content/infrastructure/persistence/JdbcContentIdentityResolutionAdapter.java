package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentIdentityResolutionPort;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 从独立身份映射表读取 Provider 身份，按状态返回固定解析结果。
 * SQL 只在适配器内出现，票务和 Agent 不会因此依赖 D 的持久化结构。
 */
@Repository
public class JdbcContentIdentityResolutionAdapter implements ContentIdentityResolutionPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentIdentityResolutionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ResolutionBatch resolve(String provider, ContentResourceType resourceType, List<String> externalIds) {
        // 空集合在应用层已拒绝；此处依赖前置校验生成至少一个占位符。
        // 调用方已限制最多一百项，这里只按实际数量生成占位符，不拼接外部值进 SQL。
        String placeholders = String.join(",", Collections.nCopies(externalIds.size(), "?"));
        // provider 与资源类型在前，外部 ID 统一作为绑定参数，避免 Provider ID 改变查询结构。
        List<Object> args = new ArrayList<>();
        args.add(provider);
        args.add(resourceType.name());
        args.addAll(externalIds);
        // 映射状态随行读取，不额外查询内容表来猜测映射是否有效。
        String sql = """
                SELECT external_id, internal_content_id, status
                  FROM content_identity_mapping
                 WHERE provider = ? AND resource_type = ? AND external_id IN (%s)
                """.formatted(placeholders);
        // 数据库理论上有唯一约束，但读取仍按列表分组，以便异常历史数据能被标为歧义而非随意选一条。
        Map<String, List<Row>> rows = jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new Row(resultSet.getString("external_id"), resultSet.getLong("internal_content_id"),
                        resultSet.getString("status")), args.toArray())
                .stream().collect(java.util.stream.Collectors.groupingBy(Row::externalId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        // 结果顺序跟去重后的请求顺序一致，调用者无需猜测数据库返回行的顺序。
        List<Resolution> results = externalIds.stream().map(id -> resolveOne(id, rows.get(id))).toList();
        // Batch 会复制结果列表，JDBC 临时集合不会被跨模块调用方修改。
        return new ResolutionBatch(results);
    }

    private Resolution resolveOne(String externalId, List<Row> rows) {
        // 未命中与映射失效要区分：前者可等待同步，后者不能继续使用旧身份。
        if (rows == null || rows.isEmpty()) {
            return new Resolution(externalId, null, ResolutionStatus.NOT_FOUND);
        }
        // ACTIVE 冲突不能通过“取第一条”掩盖，否则可能把外部排期关联到错误内容。
        long activeCount = rows.stream().filter(row -> "ACTIVE".equals(row.status())).count();
        if (activeCount > 1 || rows.stream().map(Row::internalContentId).distinct().count() > 1
                && activeCount > 0) {
            // 冲突只给固定 AMBIGUOUS，不返回多个内部 ID 让调用方自行选择。
            return new Resolution(externalId, null, ResolutionStatus.AMBIGUOUS);
        }
        Row row = rows.getFirst();
        // 唯一 ACTIVE 行才可继续返回内部 ID；其它情形均已被前置规则隔离。
        // INVALID 是终态映射，保留它以阻止过期外部 ID 被悄悄复用。
        if ("INVALID".equals(row.status())) {
            return new Resolution(externalId, null, ResolutionStatus.INVALIDATED);
        }
        return new Resolution(externalId, row.internalContentId(), ResolutionStatus.RESOLVED);
    }

    /** JDBC 临时行不穿过适配器边界，避免其它模块依赖存储状态字符串。 */
    private record Row(String externalId, long internalContentId, String status) { }
}
