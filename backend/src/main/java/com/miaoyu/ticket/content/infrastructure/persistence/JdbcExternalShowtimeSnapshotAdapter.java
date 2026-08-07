package com.miaoyu.ticket.content.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort;
import com.miaoyu.ticket.content.application.ExternalShowtimeSnapshotPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 复用 D 已有的 external_data_snapshot 保存标准化排期结果。
 *
 * <p>一个日期和影院集合只保留一份最新快照；查询键使用 SHA-256，避免把一长串内部影院 ID 直接写入外部 ID 列。</p>
 * <p>快照 payload 只保存 D 的标准化候选 DTO。</p>
 * <p>Provider 失败不会删除上一份成功快照。</p>
 * <p>过期由 Application 层按照业务时钟判断。</p>
 * <p>写入使用现有 external_data_snapshot 表，不新增票务表。</p>
 * <p>重复日期和影院集合执行幂等更新。</p>
 * <p>影院集合排序后生成稳定摘要键。</p>
 * <p>摘要键不暴露本地影院 ID 明文到外部 ID 字段。</p>
 * <p>快照读取失败转换为内部不可用，不返回原始 JSON。</p>
 * <p>快照序列化失败不会伪造成功状态。</p>
 * <p>dataAt 和 expiresAt 使用带偏移的业务时间。</p>
 * <p>候选对象不包含余座、座位、订单、支付或退款。</p>
 * <p>快照适配器不访问 A 的 Mapper 和 Repository。</p>
 * <p>业务 ID 生成器只用于新行主键，更新依赖唯一键。</p>
 * <p>数据库唯一键保证同一范围不会出现多份快照。</p>
 * <p>快照表更新不参与 Provider 网络事务。</p>
 * <p>清理和保留策略由上层验证任务控制。</p>
 * <p>该类不负责将快照直接导入本地场次。</p>
 * <p>该类不负责决定 A 的本地价格。</p>
 * <p>读取查询不锁定票务业务行。</p>
 * <p>写入只更新自己的快照记录。</p>
 * <p>唯一键冲突表示同一查询范围。</p>
 * <p>更新不会删除其他日期的快照。</p>
 * <p>快照内容由 Jackson 统一编解码。</p>
 * <p>读取异常转换为稳定内部异常。</p>
 * <p>写入异常不会被吞掉。</p>
 * <p>摘要算法固定为 SHA-256。</p>
 * <p>摘要输入使用 UTF-8。</p>
 */
@Repository
public class JdbcExternalShowtimeSnapshotAdapter implements ExternalShowtimeSnapshotPort {

    private static final String PROVIDER = "EXTERNAL_SHOWTIME_SNAPSHOT";
    private static final String DATA_TYPE = "SHOWTIME_CANDIDATE";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessIdGenerator idGenerator;

    public JdbcExternalShowtimeSnapshotAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                               BusinessIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    /** 快照读取只返回序列化后的标准化 DTO，过期判断由 Application 按业务时钟完成。 */
    public Optional<Snapshot> find(LocalDate showDate, List<Long> cinemaIds) {
        List<Snapshot> snapshots = jdbcTemplate.query("""
                SELECT payload_json, data_time, expire_time
                  FROM external_data_snapshot
                 WHERE provider = ? AND external_id = ? AND data_type = ?
                """, (resultSet, rowNumber) -> new Snapshot(read(resultSet.getString("payload_json")),
                resultSet.getTimestamp("data_time").toLocalDateTime().atOffset(java.time.ZoneOffset.ofHours(8)),
                resultSet.getTimestamp("expire_time").toLocalDateTime().atOffset(java.time.ZoneOffset.ofHours(8))),
                PROVIDER, key(showDate, cinemaIds), DATA_TYPE);
        return snapshots.stream().findFirst();
    }

    @Override
    /** 使用日期和影院集合的固定键幂等更新，不清理上一份成功快照。 */
    public void save(LocalDate showDate, List<Long> cinemaIds, Snapshot snapshot) {
        Timestamp dataAt = Timestamp.from(snapshot.dataAt().toInstant());
        Timestamp expiresAt = Timestamp.from(snapshot.expiresAt().toInstant());
        jdbcTemplate.update("""
                INSERT INTO external_data_snapshot (id, provider, external_id, data_type, payload_json,
                data_time, expire_time, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), data_time = VALUES(data_time),
                expire_time = VALUES(expire_time), version = version + 1, update_time = VALUES(update_time)
                """, idGenerator.nextId(), PROVIDER, key(showDate, cinemaIds), DATA_TYPE, write(snapshot.snapshots()),
                dataAt, expiresAt, dataAt, dataAt);
    }

    private List<ExternalShowtimeQueryPort.ExternalShowtimeSnapshot> read(String payload) {
        // JSON 解析失败属于快照不可用；不把原始 JSON 返回到日志或 HTTP 响应。
        try {
            return objectMapper.readValue(payload, new TypeReference<>() { });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("排期快照无法读取", exception);
        }
    }

    private String write(List<ExternalShowtimeQueryPort.ExternalShowtimeSnapshot> snapshots) {
        // 只序列化公开候选字段，余座、座位图、订单和 Provider 请求参数不在对象模型中。
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("排期快照无法保存", exception);
        }
    }

    private static String key(LocalDate showDate, List<Long> cinemaIds) {
        // 排序后生成稳定键，使同一影院集合不同传入顺序仍命中同一快照。
        String plain = showDate + ":" + cinemaIds.stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }
}
