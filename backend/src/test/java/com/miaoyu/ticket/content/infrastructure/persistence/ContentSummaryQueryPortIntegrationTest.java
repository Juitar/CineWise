package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ContentSummaryQueryPortIntegrationTest {

    @Autowired
    private ContentSummaryQueryPort contentSummaryQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedCinema() {
        LocalDateTime dataTime = LocalDateTime.of(2027, 8, 3, 9, 0);
        jdbcTemplate.update("DELETE FROM cinema WHERE id = ?", 8_100_001L);
        jdbcTemplate.update("""
                INSERT INTO cinema (id, source_cinema_id, name, city_code, area, address, longitude, latitude,
                source_type, source, data_time, expires_at, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, 8_100_001L, "summary-cinema", "摘要影城", "330100", "西湖区", "测试路 1 号",
                new java.math.BigDecimal("120.1302600"), new java.math.BigDecimal("30.2741500"), "MOCK",
                "test-provider", Timestamp.valueOf(dataTime), Timestamp.valueOf(dataTime.plusHours(6)),
                Timestamp.valueOf(dataTime), Timestamp.valueOf(dataTime));
    }

    @Test
    void givenExistingCinema_whenAQueriesPublicSummary_thenAddressSourceAndTimeFieldsAreReturned() {
        LocalDateTime dataTime = LocalDateTime.of(2027, 8, 3, 9, 0);

        ContentSummaryQueryPort.CinemaSummary summary = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(8_100_001L))
                .findByCinemaId(8_100_001L)
                .orElseThrow();

        // A 通过公开端口取得展示字段，不需要读 D 的 cinema 表或使用 D 的 Mapper。
        assertThat(summary.name()).isEqualTo("摘要影城");
        assertThat(summary.area()).isEqualTo("西湖区");
        assertThat(summary.address()).isEqualTo("测试路 1 号");
        assertThat(summary.source()).isEqualTo("test-provider");
        assertThat(summary.dataTime()).isEqualTo(dataTime);
        assertThat(summary.expiresAt()).isEqualTo(dataTime.plusHours(6));
        assertThat(summary.expired()).isFalse();
    }

    @Test
    void givenPartiallyMissingCinemaIds_whenBatchQuery_thenMissingIdsAreExplicit() {
        ContentSummaryQueryPort.CinemaSummaryBatch batch = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(8_100_001L, 8_199_999L));

        assertThat(batch.findByCinemaId(8_100_001L)).isPresent();
        assertThat(batch.missingCinemaIds()).containsExactly(8_199_999L);
    }

    @Test
    void givenEmptyCinemaIds_whenBatchQuery_thenItDoesNotReadContentStorage() {
        ContentSummaryQueryPort.CinemaSummaryBatch batch = contentSummaryQueryPort.findCinemaSummaries(Set.of());

        assertThat(batch.cinemas()).isEmpty();
        assertThat(batch.missingCinemaIds()).isEmpty();
    }

    @Test
    void givenAllMissingCinemaIds_whenBatchQuery_thenAllIdsAreExplicitlyMissing() {
        ContentSummaryQueryPort.CinemaSummaryBatch batch = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(8_199_998L, 8_199_999L));

        assertThat(batch.cinemas()).isEmpty();
        assertThat(batch.missingCinemaIds()).containsExactlyInAnyOrder(8_199_998L, 8_199_999L);
    }

    @Test
    void givenLogicallyDeletedCinema_whenBatchQuery_thenItIsReportedAsMissing() {
        jdbcTemplate.update("UPDATE cinema SET deleted_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2027, 8, 3, 10, 0)), 8_100_001L);

        ContentSummaryQueryPort.CinemaSummaryBatch batch = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(8_100_001L));

        assertThat(batch.cinemas()).isEmpty();
        assertThat(batch.missingCinemaIds()).containsExactly(8_100_001L);
    }
}
