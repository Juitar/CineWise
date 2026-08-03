package com.miaoyu.ticket.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentSnapshotPort;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 标准化内容快照适配器，保存查询结果而不是外部 Provider 的原始结构。
 *
 * <p>快照存在 MySQL 而不是 Redis，是为了在缓存清空、Redis 不可用或应用重启后仍能提供最近一次可追溯
 * 的内容。它不是票务事实表，不能写入场次、票价、座位和库存。</p>
 *
 * <p>同一查询通过 provider、externalId、dataType 唯一定位。保存新内容会替换该查询的旧快照并提升
 * version，避免长期积累不同版本的相同查询结果。</p>
 *
 * <p>是否能使用过期快照由 ContentQueryService 的业务时钟和最大陈旧期决定。适配器只忠实读写已记录的
 * 时间，不能擅自延长有效期。</p>
 */
@Repository
public class JdbcContentSnapshotAdapter implements ContentSnapshotPort {

    private static final String PROVIDER = "CONTENT_SNAPSHOT";

    private final JdbcTemplate jdbcTemplate;
    private final ContentResultCodec codec;
    private final BusinessIdGenerator idGenerator;

    /** 雪花 ID 只在首次快照插入使用，重复查询更新既有唯一记录。 */
    /** provider 常量隔离内容快照，避免和天气、路线等未来外部快照混用。 */
    public JdbcContentSnapshotAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                      BusinessIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.codec = new ContentResultCodec(objectMapper);
        this.idGenerator = idGenerator;
    }

    /**
     * 同一个规范化查询只保留最新快照，读取过期与否由 Application Service 按业务 Clock 判断。
     *
     * <p>未命中返回空 Optional，而不是创建空快照；空数据与 Provider 故障必须由调用用例继续区分。</p>
     */
    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
        // 外部 ID 使用同一缓存键，保证缓存和快照对完全相同的查询条件具有相同身份。
        List<String> payloads = jdbcTemplate.query("""
                SELECT payload_json FROM external_data_snapshot
                 WHERE provider = ? AND external_id = ? AND data_type = ?
                """, (resultSet, rowNumber) -> resultSet.getString("payload_json"), PROVIDER,
                ContentCacheKeyFactory.create(query), query.resourceType().name());
        return payloads.isEmpty() ? Optional.empty()
                : Optional.of(codec.read(payloads.getFirst(), query, ContentFallbackType.SNAPSHOT, false));
    }

    /**
     * MySQL 与 H2 MySQL 模式都支持该唯一键更新语句，避免为同一查询累积无效旧快照。
     *
     * <p>首次插入才使用雪花 ID；冲突更新不会改变既有 ID，便于审计记录保持稳定引用。</p>
     */
    @Override
    public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
        jdbcTemplate.update("""
                INSERT INTO external_data_snapshot (id, provider, external_id, data_type, payload_json,
                data_time, expire_time, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), data_time = VALUES(data_time),
                expire_time = VALUES(expire_time), version = version + 1, update_time = VALUES(update_time)
                """, idGenerator.nextId(), PROVIDER, ContentCacheKeyFactory.create(query), query.resourceType().name(),
                codec.write(result), Timestamp.valueOf(result.dataTime()), Timestamp.valueOf(result.expiresAt()),
                Timestamp.valueOf(result.dataTime()), Timestamp.valueOf(result.dataTime()));
    }
}
