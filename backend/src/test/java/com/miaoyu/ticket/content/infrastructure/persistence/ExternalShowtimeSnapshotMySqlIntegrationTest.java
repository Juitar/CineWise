package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort;
import com.miaoyu.ticket.content.application.ExternalShowtimeSnapshotPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 在 A 授权的隔离 MySQL 库验证排期快照的真实写入语句。
 *
 * <p>默认跳过；测试不发外部请求，只使用 2099 年的专用时间戳定位和清理自己的行。</p>
 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_SHOWTIME_MYSQL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "cinewise.seed.enabled=false",
        "cinewise.scheduling.enabled=false",
        "cinewise.content.netstart.enabled=false",
        "management.health.redis.enabled=false",
        "cinewise.auth.jwt-secret=showtime-mysql-it-jwt-secret-at-least-32-bytes",
        "cinewise.auth.audit-hash-secret=showtime-mysql-it-audit-secret-at-least-32-bytes",
        "cinewise.auth.verification.hash-secret=showtime-mysql-it-verification-secret-at-least-32-bytes",
        "cinewise.auth.verification.digits=6",
        "cinewise.auth.verification.ttl=5m",
        "cinewise.auth.verification.cooldown=60s",
        "cinewise.auth.verification.maximum-attempts=5",
        "cinewise.auth.verification.ip-window=5m",
        "cinewise.auth.verification.maximum-ip-requests=10",
        "cinewise.auth.verification.mail.smtp-enabled=false",
        "cinewise.auth.verification.mail.subject=showtime mysql it",
        "cinewise.auth.registration.invite-hash-secret=showtime-mysql-it-invite-secret-at-least-32-bytes",
        "cinewise.auth.registration.current-privacy-policy-version=showtime-privacy-v1",
        "cinewise.auth.registration.initial-invite.enabled=false"
})
class ExternalShowtimeSnapshotMySqlIntegrationTest {

    private static final String DATABASE = System.getenv("CINEWISE_CONTENT_MYSQL_DATABASE");
    private static final String PROVIDER = "EXTERNAL_SHOWTIME_SNAPSHOT";
    private static final String DATA_TYPE = "SHOWTIME_CANDIDATE";
    private static final OffsetDateTime DATA_AT = OffsetDateTime.parse("2099-01-02T03:04:05+08:00");
    private static final LocalDate SHOW_DATE = LocalDate.of(2099, 1, 2);
    private static final List<Long> CINEMA_IDS = List.of(9_400_021L, 9_400_022L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcExternalShowtimeSnapshotAdapter snapshotAdapter;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM external_data_snapshot WHERE provider = ? AND data_type = ? AND data_time = ?",
                PROVIDER, DATA_TYPE, java.sql.Timestamp.from(DATA_AT.toInstant()));
        Integer residue = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM external_data_snapshot
                 WHERE provider = ? AND data_type = ? AND data_time = ?
                """, Integer.class, PROVIDER, DATA_TYPE, java.sql.Timestamp.from(DATA_AT.toInstant()));
        assertThat(residue).isZero();
    }

    @Test
    void givenDedicatedMySqlDatabase_whenSavingSameQueryTwice_thenItUpdatesOneSnapshotAndCanReadItBack() {
        assertThat(DATABASE).as("必须通过环境变量指定隔离测试库").isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(DATABASE);
        cleanup();
        snapshotAdapter.save(SHOW_DATE, CINEMA_IDS, snapshot("mysql-s1"));
        snapshotAdapter.save(SHOW_DATE, List.of(9_400_022L, 9_400_021L), snapshot("mysql-s2"));

        assertThat(snapshotAdapter.find(SHOW_DATE, CINEMA_IDS)).hasValueSatisfying(snapshot ->
                assertThat(snapshot.snapshots())
                        .extracting(ExternalShowtimeQueryPort.ExternalShowtimeSnapshot::externalShowId)
                        .containsExactly("mysql-s2"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM external_data_snapshot
                 WHERE provider = ? AND data_type = ? AND data_time = ?
                """, Integer.class, PROVIDER, DATA_TYPE, java.sql.Timestamp.from(DATA_AT.toInstant()))).isEqualTo(1);
    }

    private static ExternalShowtimeSnapshotPort.Snapshot snapshot(String showId) {
        OffsetDateTime expiresAt = DATA_AT.plusMinutes(10);
        return new ExternalShowtimeSnapshotPort.Snapshot(List.of(new ExternalShowtimeQueryPort.ExternalShowtimeSnapshot(
                "NETSTART_MAOYAN", showId, "movie-mysql-it", "cinema-mysql-it", 9_400_101L, 9_400_021L,
                DATA_AT.plusHours(2), null, new BigDecimal("36.00"),
                ExternalShowtimeQueryPort.PriceSemantic.REFERENCE_ONLY, DATA_AT, expiresAt, false, false, null,
                ExternalShowtimeQueryPort.QualityStatus.ACCEPTED,
                new ExternalShowtimeQueryPort.ExternalShowtimeKey("NETSTART_MAOYAN", "cinema-mysql-it", showId))),
                DATA_AT, expiresAt);
    }
}
