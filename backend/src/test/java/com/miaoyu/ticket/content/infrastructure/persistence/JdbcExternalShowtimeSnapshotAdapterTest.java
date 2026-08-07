package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort;
import com.miaoyu.ticket.content.application.ExternalShowtimeSnapshotPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcExternalShowtimeSnapshotAdapterTest {

    @Test
    void givenSameDateAndCinemaSet_whenSavedTwice_thenItReplacesOneDeterministicSnapshot() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:showtime-snapshot;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE external_data_snapshot (
                    id BIGINT PRIMARY KEY, provider VARCHAR(64), external_id VARCHAR(128), data_type VARCHAR(32),
                    payload_json VARCHAR(20000), data_time TIMESTAMP, expire_time TIMESTAMP, version BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP,
                    UNIQUE(provider, external_id, data_type))
                """);
        JdbcExternalShowtimeSnapshotAdapter adapter = new JdbcExternalShowtimeSnapshotAdapter(jdbc,
                JsonMapper.builder().findAndAddModules().build(), new IncrementingIds());
        LocalDate date = LocalDate.of(2026, 8, 7);
        ExternalShowtimeSnapshotPort.Snapshot first = snapshot("s1", "2026-08-07T09:10:00+08:00");
        ExternalShowtimeSnapshotPort.Snapshot replacement = snapshot("s2", "2026-08-07T09:09:00+08:00");

        adapter.save(date, List.of(9L, 2L), first);
        adapter.save(date, List.of(2L, 9L), replacement);

        assertThat(adapter.find(date, List.of(9L, 2L))).hasValueSatisfying(snapshot ->
                assertThat(snapshot.snapshots())
                        .extracting(ExternalShowtimeQueryPort.ExternalShowtimeSnapshot::externalShowId)
                        .containsExactly("s2"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_data_snapshot", Integer.class)).isEqualTo(1);
    }

    private static ExternalShowtimeSnapshotPort.Snapshot snapshot(String showId, String expiresAt) {
        OffsetDateTime dataAt = OffsetDateTime.parse("2026-08-07T09:00:00+08:00");
        return new ExternalShowtimeSnapshotPort.Snapshot(List.of(new ExternalShowtimeQueryPort.ExternalShowtimeSnapshot(
                "NETSTART_MAOYAN", showId, "m1", "c1", 11L, 21L,
                OffsetDateTime.parse("2026-08-07T11:00:00+08:00"), null, null,
                ExternalShowtimeQueryPort.PriceSemantic.REFERENCE_ONLY, dataAt,
                OffsetDateTime.parse(expiresAt), false)),
                dataAt, OffsetDateTime.parse(expiresAt));
    }

    /** 测试主键只验证首插入和冲突更新，不依赖运行时雪花实现。 */
    private static final class IncrementingIds implements com.miaoyu.ticket.common.id.BusinessIdGenerator {
        private long next = 1L;
        @Override public long nextId() { return next++; }
    }
}
