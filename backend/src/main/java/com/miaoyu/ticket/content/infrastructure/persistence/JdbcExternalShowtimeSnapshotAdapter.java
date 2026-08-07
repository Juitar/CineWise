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
        try {
            return objectMapper.readValue(payload, new TypeReference<>() { });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("排期快照无法读取", exception);
        }
    }

    private String write(List<ExternalShowtimeQueryPort.ExternalShowtimeSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("排期快照无法保存", exception);
        }
    }

    private static String key(LocalDate showDate, List<Long> cinemaIds) {
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
