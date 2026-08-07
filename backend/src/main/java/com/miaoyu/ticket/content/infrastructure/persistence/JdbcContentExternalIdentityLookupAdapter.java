package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentExternalIdentityLookupPort;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D 在访问排期 Provider 前按本地内容 ID 反查已经确认的外部身份，绝不以名称补猜。 */
@Repository
public class JdbcContentExternalIdentityLookupAdapter implements ContentExternalIdentityLookupPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentExternalIdentityLookupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExternalIdentity> findActiveExternalIds(String provider, ContentResourceType resourceType,
                                                        List<Long> contentIds) {
        if (contentIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(contentIds));
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        Object[] args = new Object[distinctIds.size() + 2];
        args[0] = provider;
        args[1] = resourceType.name();
        for (int index = 0; index < distinctIds.size(); index++) {
            args[index + 2] = distinctIds.get(index);
        }
        String cityColumn = resourceType == ContentResourceType.CINEMA ? "c.provider_city_id" : "NULL";
        String join = resourceType == ContentResourceType.CINEMA
                ? " JOIN cinema c ON c.id = m.internal_content_id" : "";
        return jdbcTemplate.query("""
                SELECT m.internal_content_id, m.external_id, %s AS provider_city_id
                  FROM content_identity_mapping m%s
                 WHERE m.provider = ? AND m.resource_type = ? AND m.status = 'ACTIVE'
                   AND m.internal_content_id IN (%s)
                 ORDER BY m.internal_content_id
                """.formatted(cityColumn, join, placeholders), (resultSet, rowNumber) -> new ExternalIdentity(
                resultSet.getLong("internal_content_id"), resultSet.getString("external_id"),
                resultSet.getString("provider_city_id")), args);
    }
}
