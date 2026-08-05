package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import(ShowQueryMySqlIntegrationTest.MySqlQueryTestConfiguration.class)
class ShowQueryMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";

    @Autowired
    private ShowQueryService showQueryService;

    @Autowired
    private SaleableShowBatchQueryService saleableShowBatchQueryService;

    @Autowired
    private AvailableDateQueryService availableDateQueryService;

    @Autowired
    private SeatQueryService seatQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void requireDedicatedDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("场次查询MySQL测试只允许操作一次性隔离库")
                .isEqualTo(REQUIRED_DATABASE);
    }

    @Test
    void givenIsolatedMySqlSeed_whenQueryShowAndSeats_thenReturnAuthoritativeSnapshots() {
        String mysqlVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(mysqlVersion).startsWith("8.4.");
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        Map<String, Object> nextShow = jdbcTemplate.queryForMap("""
                SELECT id, movie_id, cinema_id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > ?
                 ORDER BY start_time, id
                 LIMIT 1
                """, Timestamp.valueOf(now));
        long showId = ((Number) nextShow.get("id")).longValue();
        long movieId = ((Number) nextShow.get("movie_id")).longValue();
        long cinemaId = ((Number) nextShow.get("cinema_id")).longValue();

        assertThat(showQueryService.queryShows(new ShowQuery(movieId, cinemaId, null, null, null)))
                .isNotEmpty()
                .allSatisfy(show -> {
                    assertThat(show.movieId()).isEqualTo(movieId);
                    assertThat(show.cinemaId()).isEqualTo(cinemaId);
                    assertThat(show.basePrice().scale()).isEqualTo(2);
                    assertThat(show.expiresAt()).isEqualTo(show.startTime());
                });
        assertThat(availableDateQueryService.queryAvailableDates(movieId, cinemaId))
                .isNotEmpty()
                .allSatisfy(date -> assertThat(date.showCount()).isPositive());
        assertThat(showQueryService.queryShows(new ShowQuery(
                movieId,
                cinemaId,
                LocalDate.now(clock).plusDays(30),
                null,
                null))).isEmpty();

        SeatMapView seatMap = seatQueryService.querySeatMap(showId);
        assertThat(seatMap.showId()).isEqualTo(showId);
        assertThat(seatMap.seatCount()).isEqualTo(80);
        assertThat(seatMap.seats()).hasSize(80);
        assertThat(seatMap.availableSeatCount()).isEqualTo(80);
    }

    @Test
    @Transactional
    void givenBatchFixtures_whenQueryMultipleCinemas_thenFilterInMySqlAndKeepStableBoundaries() {
        LocalDate queryDate = LocalDate.now(clock).plusDays(1);
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        long cinemaOne = 9_940_000_001L;
        long cinemaTwo = 9_940_000_002L;
        long auditoriumOne = 9_941_000_001L;
        long auditoriumTwo = 9_941_000_002L;
        insertAuditorium(auditoriumOne, cinemaOne, "批量查询一号厅", createdAt);
        insertAuditorium(auditoriumTwo, cinemaTwo, "批量查询二号厅", createdAt);

        insertShowWithSeat(9_942_000_001L, 9_943_000_001L, cinemaOne, auditoriumOne,
                queryDate.atTime(10, 0), "ON_SALE", "AVAILABLE", createdAt);
        insertShowWithSeat(9_942_000_002L, 9_943_000_002L, cinemaTwo, auditoriumTwo,
                queryDate.atTime(11, 0), "ON_SALE", "AVAILABLE", createdAt);
        insertShowWithSeat(9_942_000_003L, 9_943_000_003L, cinemaOne, auditoriumOne,
                queryDate.atTime(12, 0), "ON_SALE", "SOLD", createdAt);
        insertShowWithSeat(9_942_000_004L, 9_943_000_004L, cinemaOne, auditoriumOne,
                queryDate.atTime(13, 0), "OFF_SALE", "AVAILABLE", createdAt);
        insertShowWithSeat(9_942_000_005L, 9_943_000_005L, cinemaOne, auditoriumOne,
                queryDate.plusDays(1).atTime(10, 0), "ON_SALE", "AVAILABLE", createdAt);
        insertShowWithSeat(9_942_000_006L, 9_943_000_006L, cinemaOne, auditoriumOne,
                queryDate.atTime(14, 0), "ON_SALE", "AVAILABLE", createdAt);
        insertShowWithSeat(9_942_000_007L, 9_943_000_007L, cinemaOne, auditoriumOne,
                queryDate.atTime(15, 0), "ON_SALE", "AVAILABLE", createdAt);

        SaleableShowBatchResult result = saleableShowBatchQueryService.query(
                new SaleableShowBatchQuery(queryDate, List.of(cinemaOne, cinemaTwo)));

        assertThat(result.truncated()).isFalse();
        assertThat(result.shows()).extracting(SaleableShowView::showId)
                .containsExactly(9_942_000_001L, 9_942_000_002L, 9_942_000_006L, 9_942_000_007L);
        assertThat(result.shows()).extracting(SaleableShowView::movieId)
                .containsExactly(9_943_000_001L, 9_943_000_002L, 9_943_000_006L, 9_943_000_007L);
        assertThat(result.shows()).extracting(SaleableShowView::cinemaId)
                .contains(cinemaOne, cinemaTwo);
        assertThat(result.shows()).allSatisfy(show -> {
            assertThat(show.availableSeatCount()).isPositive();
            assertThat(show.saleable()).isTrue();
            assertThat(show.price().scale()).isEqualTo(2);
            assertThat(show.dataType()).isEqualTo("MOCK");
            assertThat(show.source()).isEqualTo("batch-query-test");
            assertThat(show.expiresAt()).isBeforeOrEqualTo(show.startTime());
            assertThat(show.expiresAt()).isEqualTo(show.dataAt().plusSeconds(60));
        });

    }

    private void insertAuditorium(long auditoriumId, long cinemaId, String name, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO auditorium
                    (id, cinema_id, name, hall_type, row_count, seat_count, data_type, status,
                     version, create_time, update_time)
                VALUES (?, ?, ?, 'NORMAL', 1, 1, 'MOCK', 'ENABLED', 0, ?, ?)
                """, auditoriumId, cinemaId, name, createdAt, createdAt);
    }

    private void insertShowWithSeat(
            long showId,
            long movieId,
            long cinemaId,
            long auditoriumId,
            LocalDateTime startTime,
            String showStatus,
            String seatStatus,
            LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO movie_show
                    (id, movie_id, cinema_id, auditorium_id, start_time, end_time, language_version,
                     base_price, data_type, source, status, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, '国语2D', 49.90, 'MOCK', 'batch-query-test', ?, 0, ?, ?)
                """, showId, movieId, cinemaId, auditoriumId, startTime, startTime.plusHours(2),
                showStatus, createdAt, createdAt);
        jdbcTemplate.update("""
                INSERT INTO show_seat
                    (id, show_id, row_no, seat_no, seat_label, status, version, create_time, update_time)
                VALUES (?, ?, 'A', '1', 'A1', ?, 0, ?, ?)
                """, showId + 100_000L, showId, seatStatus, createdAt, createdAt);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlQueryTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(9000001L, RoleCode.USER, 0L);
        }
    }
}
