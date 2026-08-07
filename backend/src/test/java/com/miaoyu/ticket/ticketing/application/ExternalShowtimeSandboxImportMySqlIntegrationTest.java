package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 在 CI 一次性 MySQL 8.4 库中验证 V019 外部三元键的事务幂等。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import({
    ShowQueryMySqlIntegrationTest.MySqlQueryTestConfiguration.class,
    ExternalShowtimeSandboxImportMySqlIntegrationTest.ContentSummaryTestConfiguration.class
})
class ExternalShowtimeSandboxImportMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";

    @Autowired
    private ExternalShowtimeSandboxPersistenceService persistenceService;

    @Autowired
    private ShowQueryService showQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void requireDedicatedDatabaseAndCleanFixtures() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo(REQUIRED_DATABASE);
        cleanFixtures();
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void givenConcurrentSameExternalKey_whenImported_thenOneMappingAndLocalShowExist() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Long> first = executor.submit(() -> persistenceService.importEntry(entry()));
            Future<Long> second = executor.submit(() -> persistenceService.importEntry(entry()));

            assertThat(first.get()).isEqualTo(second.get());
        }

        assertThat(count("SELECT COUNT(*) FROM ticketing_external_showtime_mapping")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM movie_show WHERE source = 'external-sandbox'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM show_seat ss JOIN movie_show ms ON ms.id = ss.show_id "
                + "WHERE ms.source = 'external-sandbox'")).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject("SELECT data_type FROM movie_show WHERE source = 'external-sandbox'",
                String.class)).isEqualTo("SANDBOX");
        assertThat(showQueryService.queryShows(new ShowQuery(7_001L, 7_002L, null, null, null)))
                .singleElement()
                .satisfies(show -> {
                    assertThat(show.dataType()).isEqualTo("SANDBOX");
                    assertThat(show.availableSeatCount()).isEqualTo(80);
                });
    }

    private ExternalShowtimeSandboxImportPlan.Entry entry() {
        LocalDateTime start = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.ofHours(8))
                .plusDays(1)
                .withSecond(0)
                .withNano(0);
        OffsetDateTime startAt = start.atOffset(ZoneOffset.ofHours(8));
        return new ExternalShowtimeSandboxImportPlan.Entry(new ExternalShowtimeSandboxReference(
                "NETSTART", "mysql-cinema", "mysql-show", 7_001L, 7_002L,
                startAt, 90, "1号厅", startAt.minusHours(1), startAt.plusMinutes(1),
                true, false, false, false), start.plusMinutes(90));
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private void cleanFixtures() {
        jdbcTemplate.update("""
                DELETE FROM ticketing_external_showtime_mapping
                 WHERE provider = 'NETSTART'
                   AND external_cinema_id = 'mysql-cinema'
                   AND external_show_id = 'mysql-show'
                """);
        jdbcTemplate.update("DELETE ss FROM show_seat ss JOIN movie_show ms ON ms.id = ss.show_id "
                + "WHERE ms.source = 'external-sandbox'");
        jdbcTemplate.update("DELETE FROM movie_show WHERE source = 'external-sandbox'");
        jdbcTemplate.update("DELETE FROM auditorium WHERE name LIKE '本地沙箱·%'");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContentSummaryTestConfiguration {

        @Bean
        @Primary
        ContentSummaryQueryPort sandboxCinemaSummaryPort() {
            return cinemaIds -> new ContentSummaryQueryPort.CinemaSummaryBatch(
                    cinemaIds.contains(7_002L)
                            ? List.of(new ContentSummaryQueryPort.CinemaSummary(
                                    7_002L, "本地沙箱测试影院", null, null, "TEST",
                                    LocalDateTime.of(2026, 8, 7, 9, 0),
                                    LocalDateTime.of(2026, 8, 7, 10, 0), false))
                            : List.of(),
                    java.util.Set.of());
        }
    }
}
