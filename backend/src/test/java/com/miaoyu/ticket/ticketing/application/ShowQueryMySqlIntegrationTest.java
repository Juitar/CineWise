package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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

@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=false",
    "management.health.redis.enabled=false"
})
@Import(ShowQueryMySqlIntegrationTest.MySqlQueryTestConfiguration.class)
class ShowQueryMySqlIntegrationTest {

    @Autowired
    private ShowQueryService showQueryService;

    @Autowired
    private SeatQueryService seatQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenCloudMySqlSeed_whenQueryShowAndSeats_thenReturnAuthoritativeSnapshots() {
        String mysqlVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(mysqlVersion).startsWith("8.4.");
        Map<String, Object> nextShow = jdbcTemplate.queryForMap("""
                SELECT id, movie_id, cinema_id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > CURRENT_TIMESTAMP(3)
                 ORDER BY start_time, id
                 LIMIT 1
                """);
        long showId = ((Number) nextShow.get("id")).longValue();
        long movieId = ((Number) nextShow.get("movie_id")).longValue();
        long cinemaId = ((Number) nextShow.get("cinema_id")).longValue();

        assertThat(showQueryService.queryShows(new ShowQuery(movieId, cinemaId, null, null, null)))
                .isNotEmpty()
                .allSatisfy(show -> {
                    assertThat(show.movieId()).isEqualTo(movieId);
                    assertThat(show.cinemaId()).isEqualTo(cinemaId);
                    assertThat(show.basePrice().scale()).isEqualTo(2);
                });
        assertThat(showQueryService.queryShows(new ShowQuery(
                movieId,
                cinemaId,
                LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(30),
                null,
                null))).isEmpty();

        SeatMapView seatMap = seatQueryService.querySeatMap(showId);
        assertThat(seatMap.showId()).isEqualTo(showId);
        assertThat(seatMap.seatCount()).isEqualTo(80);
        assertThat(seatMap.seats()).hasSize(80);
        assertThat(seatMap.availableSeatCount()).isEqualTo(80);
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
