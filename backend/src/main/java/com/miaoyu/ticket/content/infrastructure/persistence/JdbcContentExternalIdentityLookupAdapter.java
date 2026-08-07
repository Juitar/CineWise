package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentExternalIdentityLookupPort;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * D 在访问排期 Provider 前按本地内容 ID 反查已经确认的外部身份，绝不以名称补猜。
 * <p>SQL 只读取 ACTIVE 映射，历史失效身份不会再次发起外部请求。</p>
 * <p>查询使用本地内部 ID，避免地址、经纬度和展示名称造成错误匹配。</p>
 * <p>影院查询额外读取 provider_city_id，影片查询明确返回空城市值。</p>
 * <p>返回对象只供 D 的 Application Service 使用，不是公开 REST DTO。</p>
 * <p>外部 ID 与本地 ID 的关系由既有身份表维护，不在适配器里自动创建。</p>
 * <p>重复输入 ID 会在 SQL 前去重，避免同一影院重复计数。</p>
 * <p>空输入直接返回空列表，不生成非法 IN 子句。</p>
 * <p>SQL 参数使用预编译占位符，调用方的 ID 不拼接进值。</p>
 * <p>排序按本地内容 ID，保证同批结果稳定。</p>
 * <p>适配器不读取票务场次、座位、订单或支付表。</p>
 * <p>适配器不记录 Provider URL、ci、Key、Cookie 或原始响应。</p>
 * <p>身份缺失由上层隔离候选并保留对应业务错误语义。</p>
 * <p>身份歧义不会按第一条记录继续执行。</p>
 * <p>城市编号只在 D 内部请求边界短暂使用。</p>
 * <p>该类不负责 Provider 限流和重试，职责由 Provider 适配器承担。</p>
 * <p>该类不负责本地交易价格或余座计算。</p>
 * <p>该类不改变既有内部 cinemaId。</p>
 * <p>任何持久化写入仍由内容同步 Owner 负责。</p>
 */
@Repository
public class JdbcContentExternalIdentityLookupAdapter implements ContentExternalIdentityLookupPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentExternalIdentityLookupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    /** SQL 只取 ACTIVE 稳定身份；外部影院城市编号不会进入公开 DTO。 */
    public List<ExternalIdentity> findActiveExternalIds(String provider, ContentResourceType resourceType,
                                                        List<Long> contentIds) {
        // 空输入不执行 SQL，避免构造空 IN 子句并减少无意义的数据库访问。
        if (contentIds.isEmpty()) {
            return List.of();
        }
        // 去重后再查询，保证同一影院不会在 Provider 请求中被重复计数。
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(contentIds));
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        Object[] args = new Object[distinctIds.size() + 2];
        args[0] = provider;
        args[1] = resourceType.name();
        for (int index = 0; index < distinctIds.size(); index++) {
            args[index + 2] = distinctIds.get(index);
        }
        // 只有影院身份需要城市参数；影片查询明确返回 NULL，防止跨资源误用城市编号。
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
