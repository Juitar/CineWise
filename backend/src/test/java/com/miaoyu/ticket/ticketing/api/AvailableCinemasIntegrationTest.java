package com.miaoyu.ticket.ticketing.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 契约验证使用 A 的真实排期和座位表；认证白名单由 C 的安全配置另行覆盖。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"cinewise.seed.enabled=true", "cinewise.seed.fixed-value=20260802"})
@AutoConfigureMockMvc
@Import(AvailableCinemasIntegrationTest.FixedClockConfiguration.class)
class AvailableCinemasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser
    @Transactional
    void givenSaleableShows_whenQuery_thenReturnSourceSeparatedCinemaPage() throws Exception {
        Map<String, Object> candidate = jdbcTemplate.queryForMap("""
                SELECT ms.movie_id, ms.cinema_id
                  FROM movie_show ms
                 WHERE ms.status = 'ON_SALE' AND ms.start_time > '2026-08-02 08:00:00'
                 ORDER BY ms.start_time, ms.id LIMIT 1
                """);
        String movieId = candidate.get("MOVIE_ID").toString();

        mockMvc.perform(get("/api/v1/shows/available-cinemas").param("movieId", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isNotEmpty())
                .andExpect(jsonPath("$.data.records[*].cinemaId", everyItem(matchesPattern("[1-9][0-9]*"))))
                .andExpect(jsonPath("$.data.records[*].availableShowCount", everyItem(greaterThan(0))))
                .andExpect(jsonPath("$.data.records[*].nearestStartTime", everyItem(endsWith("+08:00"))));
    }

    @Test
    @WithMockUser
    @Transactional
    void givenAllSeatsSoldForCinema_whenQuery_thenExcludeThatCinema() throws Exception {
        Map<String, Object> candidate = jdbcTemplate.queryForMap("""
                SELECT ms.movie_id, ms.cinema_id
                  FROM movie_show ms
                 WHERE ms.status = 'ON_SALE' AND ms.start_time > '2026-08-02 08:00:00'
                 ORDER BY ms.start_time, ms.id LIMIT 1
                """);
        String movieId = candidate.get("MOVIE_ID").toString();
        String cinemaId = candidate.get("CINEMA_ID").toString();
        jdbcTemplate.update("""
                UPDATE show_seat SET status = 'SOLD'
                 WHERE show_id IN (SELECT id FROM movie_show WHERE movie_id = ? AND cinema_id = ?)
                """, movieId, cinemaId);

        mockMvc.perform(get("/api/v1/shows/available-cinemas").param("movieId", movieId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"cinemaId\":\"" + cinemaId + "\""))));
    }

    @Test
    @WithMockUser
    void givenInvalidOrUnknownMovie_whenQuery_thenReturn400OrEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/shows/available-cinemas").param("movieId", "01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(100001));
        mockMvc.perform(get("/api/v1/shows/available-cinemas").param("movieId", "999999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
