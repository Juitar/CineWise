package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import com.miaoyu.ticket.ticketing.infrastructure.persistence.ShowQueryRow;
import com.miaoyu.ticket.ticketing.infrastructure.persistence.TicketingQueryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties = "cinewise.seed.enabled=true")
@Import(AvailableDateQueryIntegrationTest.FixedClockConfiguration.class)
class AvailableDateQueryIntegrationTest {

    private static final long TEST_MOVIE_ID = 9_930_000_001L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private AvailableDateQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketingQueryMapper ticketingQueryMapper;

    @Test
    @Transactional
    void givenMixedShowFacts_whenQueryAvailableDates_thenAggregateOnlyStatusAndTimeMatches() {
        long cinemaId = jdbcTemplate.queryForObject("SELECT MIN(cinema_id) FROM auditorium", Long.class);
        long auditoriumId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM auditorium WHERE cinema_id = ?",
                Long.class,
                cinemaId);

        insertShow(9_930_100_001L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 2, 8, 0), "ON_SALE");
        insertShow(9_930_100_002L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 2, 8, 0, 0, 1_000_000), "ON_SALE");
        insertShow(9_930_100_003L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 2, 14, 13), "ON_SALE");
        insertShow(9_930_100_004L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 3, 10, 13), "ON_SALE");
        insertShow(9_930_100_005L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 4, 11, 13), "STOPPED");
        insertShow(9_930_100_006L, TEST_MOVIE_ID, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 9, 0, 0), "ON_SALE");
        insertShow(9_930_100_007L, TEST_MOVIE_ID + 1, cinemaId, auditoriumId,
                LocalDateTime.of(2026, 8, 5, 12, 13), "ON_SALE");
        insertShow(9_930_100_008L, TEST_MOVIE_ID, cinemaId + 1, auditoriumId,
                LocalDateTime.of(2026, 8, 6, 13, 13), "ON_SALE");

        List<AvailableDateView> result = queryService.queryAvailableDates(TEST_MOVIE_ID, cinemaId);

        assertThat(result).containsExactly(
                new AvailableDateView(LocalDate.of(2026, 8, 2), 2),
                new AvailableDateView(LocalDate.of(2026, 8, 3), 1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM show_seat WHERE show_id BETWEEN ? AND ?",
                Integer.class,
                9_930_100_001L,
                9_930_100_008L)).isZero();
    }

    @Test
    @Transactional
    void givenExternalSandboxOnDate_whenQueryAvailableDates_thenItHidesSameDayDemoSeed() {
        long cinemaId = jdbcTemplate.queryForObject("SELECT MIN(cinema_id) FROM auditorium", Long.class);
        List<Long> auditoriumIds = jdbcTemplate.queryForList(
                "SELECT id FROM auditorium WHERE cinema_id = ? ORDER BY id", Long.class, cinemaId);
        long demoAuditoriumId = auditoriumIds.getFirst();
        long realAuditoriumId = auditoriumIds.getLast();

        insertShow(9_930_200_001L, TEST_MOVIE_ID, cinemaId, demoAuditoriumId,
                LocalDateTime.of(2026, 8, 2, 10, 0), "ON_SALE", "demo-seed");
        insertShow(9_930_200_002L, TEST_MOVIE_ID + 1, cinemaId, realAuditoriumId,
                LocalDateTime.of(2026, 8, 2, 11, 0), "ON_SALE", "external-sandbox");
        insertShow(9_930_200_003L, TEST_MOVIE_ID, cinemaId, demoAuditoriumId,
                LocalDateTime.of(2026, 8, 3, 10, 0), "ON_SALE", "demo-seed");

        List<AvailableDateView> result = queryService.queryAvailableDates(TEST_MOVIE_ID, cinemaId);

        assertThat(result).containsExactly(new AvailableDateView(LocalDate.of(2026, 8, 3), 1));
        assertThat(querySources(TEST_MOVIE_ID, cinemaId, LocalDate.of(2026, 8, 2))).isEmpty();
        assertThat(querySources(TEST_MOVIE_ID + 1, cinemaId, LocalDate.of(2026, 8, 2)))
                .containsExactly("external-sandbox");
        assertThat(querySources(TEST_MOVIE_ID, cinemaId, LocalDate.of(2026, 8, 3)))
                .containsExactly("demo-seed");
    }

    /** 直接读取 A 的查询投影，验证页面查询收到的不是被掩盖的 Mock 行。 */
    private List<String> querySources(long movieId, long cinemaId, LocalDate showDate) {
        return ticketingQueryMapper.findSaleableShows(new ShowQueryRepository.QueryCriteria(
                        movieId,
                        cinemaId,
                        LocalDateTime.of(2026, 8, 2, 8, 0),
                        LocalDateTime.of(2026, 8, 9, 0, 0),
                        showDate.atStartOfDay(),
                        showDate.plusDays(1).atStartOfDay(),
                        null,
                        null))
                .stream()
                .map(ShowQueryRow::source)
                .toList();
    }

    private void insertShow(
            long showId,
            long movieId,
            long cinemaId,
            long auditoriumId,
                LocalDateTime startTime,
                String status) {
        insertShow(showId, movieId, cinemaId, auditoriumId, startTime, status, "available-date-test");
    }

    private void insertShow(
            long showId,
            long movieId,
            long cinemaId,
            long auditoriumId,
            LocalDateTime startTime,
            String status,
            String source) {
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, '国语', ?, 'MOCK', ?, ?, 0, ?, ?)
                """,
                showId,
                movieId,
                cinemaId,
                auditoriumId,
                startTime,
                startTime.plusHours(2),
                new BigDecimal("68.00"),
                source,
                status,
                startTime.minusDays(1),
                startTime.minusDays(1));
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
