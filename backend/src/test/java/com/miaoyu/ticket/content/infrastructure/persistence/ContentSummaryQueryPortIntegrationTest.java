package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
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

    @Test
    void givenExistingCinema_whenAQueriesPublicSummary_thenAreaAndTimeFieldsAreReturnedWithoutAccessingMapper() {
        LocalDateTime dataTime = LocalDateTime.of(2027, 8, 3, 9, 0);
        jdbcTemplate.update("""
                INSERT INTO cinema (id, source_cinema_id, name, city_code, area, address, longitude, latitude,
                source_type, source, data_time, expires_at, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, 8_100_001L, "summary-cinema", "摘要影城", "330100", "西湖区", "测试路 1 号",
                new java.math.BigDecimal("120.1302600"), new java.math.BigDecimal("30.2741500"), "MOCK",
                "test-provider", Timestamp.valueOf(dataTime), Timestamp.valueOf(dataTime.plusHours(6)),
                Timestamp.valueOf(dataTime), Timestamp.valueOf(dataTime));

        ContentSummaryQueryPort.CinemaSummary summary = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(8_100_001L)).get(8_100_001L);

        // A 通过公开端口取得展示字段，不需要读 D 的 cinema 表或使用 D 的 Mapper。
        assertThat(summary.name()).isEqualTo("摘要影城");
        assertThat(summary.area()).isEqualTo("西湖区");
        assertThat(summary.dataTime()).isEqualTo(dataTime);
        assertThat(summary.expiresAt()).isEqualTo(dataTime.plusHours(6));
        assertThat(summary.expired()).isFalse();
    }
}
