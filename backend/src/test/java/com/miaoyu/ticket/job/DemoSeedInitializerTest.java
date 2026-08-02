package com.miaoyu.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(DemoSeedInitializerTest.FixedClockConfiguration.class)
class DemoSeedInitializerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private DemoSeedInitializer initializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenExistingSeedAndLockedSeat_whenSeedRunsAgain_thenCountsStayStableAndSeatStateIsPreserved() {
        assertSeedCounts();
        assertScheduleWindow();

        Long seatId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM show_seat", Long.class);
        int updated = jdbcTemplate.update("""
                UPDATE show_seat
                   SET status = 'LOCKED',
                       lock_order_no = 'SEED-PRESERVE-ORDER',
                       lock_expire_time = ?,
                       version = 7
                 WHERE id = ?
                """, Timestamp.from(FIXED_INSTANT.plusSeconds(900)), seatId);
        assertThat(updated).isOne();

        initializer.initialize();

        assertSeedCounts();
        Map<String, Object> seat = jdbcTemplate.queryForMap("""
                SELECT status, lock_order_no, version
                  FROM show_seat
                 WHERE id = ?
                """, seatId);
        assertThat(seat.get("STATUS")).isEqualTo("LOCKED");
        assertThat(seat.get("LOCK_ORDER_NO")).isEqualTo("SEED-PRESERVE-ORDER");
        assertThat(((Number) seat.get("VERSION")).intValue()).isEqualTo(7);
    }

    private void assertSeedCounts() {
        assertThat(count("movie")).isEqualTo(10);
        assertThat(count("cinema")).isEqualTo(4);
        assertThat(count("auditorium")).isEqualTo(8);
        assertThat(count("movie_show")).isEqualTo(168);
        assertThat(count("show_seat")).isEqualTo(13_440);
    }

    private void assertScheduleWindow() {
        Timestamp firstShow = jdbcTemplate.queryForObject(
                "SELECT MIN(start_time) FROM movie_show",
                Timestamp.class);
        Timestamp lastShow = jdbcTemplate.queryForObject(
                "SELECT MAX(start_time) FROM movie_show",
                Timestamp.class);
        assertThat(firstShow).isEqualTo(Timestamp.valueOf("2026-08-02 09:30:00"));
        assertThat(lastShow).isEqualTo(Timestamp.valueOf("2026-08-08 19:30:00"));
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }
    }
}
