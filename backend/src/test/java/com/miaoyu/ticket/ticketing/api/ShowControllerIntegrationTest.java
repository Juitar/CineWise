package com.miaoyu.ticket.ticketing.api;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
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
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@AutoConfigureMockMvc
@Import(ShowControllerIntegrationTest.QueryTestConfiguration.class)
class ShowControllerIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenFixedMovieAndCinema_whenQueryShows_thenReturnFrozenContractAndTimeFilter() throws Exception {
        Map<String, Object> show = jdbcTemplate.queryForMap("""
                SELECT movie_id, cinema_id
                  FROM movie_show
                 WHERE start_time = '2026-08-02 14:00:00'
                 ORDER BY id
                 LIMIT 1
                """);
        String movieId = show.get("MOVIE_ID").toString();
        String cinemaId = show.get("CINEMA_ID").toString();

        mockMvc.perform(get("/api/v1/shows")
                        .param("movieId", movieId)
                        .param("cinemaId", cinemaId)
                        .param("date", "2026-08-02")
                        .param("timeFrom", "13:00")
                        .param("timeTo", "15:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[*].movieId", everyItem(matchesPattern("\\d+"))))
                .andExpect(jsonPath("$.data[*].cinemaId", everyItem(matchesPattern("\\d+"))))
                .andExpect(jsonPath("$.data[*].showId", everyItem(matchesPattern("\\d+"))))
                .andExpect(jsonPath("$.data[*].basePrice", everyItem(matchesPattern("\\d+\\.\\d{2}"))))
                .andExpect(jsonPath("$.data[*].startTime", everyItem(endsWith("+08:00"))))
                .andExpect(jsonPath("$.data[*].status", everyItem(matchesPattern("ON_SALE"))))
                .andExpect(jsonPath("$.data[*].dataType", everyItem(matchesPattern("MOCK"))));
    }

    @Test
    void givenMissingOrInvalidFilters_whenQueryShows_thenReturnBadRequestOrEmptyResult() throws Exception {
        String cinemaId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM cinema", String.class);
        mockMvc.perform(get("/api/v1/shows").param("cinemaId", cinemaId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));

        String movieId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM movie", String.class);
        mockMvc.perform(get("/api/v1/shows")
                        .param("movieId", movieId)
                        .param("cinemaId", cinemaId)
                        .param("timeFrom", "19:00")
                        .param("timeTo", "09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));

        mockMvc.perform(get("/api/v1/shows")
                        .param("movieId", movieId)
                        .param("cinemaId", cinemaId)
                        .param("date", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void givenTicketingEndpoints_whenReadOpenApi_thenExposeReadAndOrderContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/shows'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/shows'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/shows/{showId}/seats'].get").exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/shows/{showId}/seats'].get.security[0].cookieAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/by-request/{clientRequestId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/payments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/payments'].post.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/payment'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tickets/{ticketId}'].get").exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/orders/{orderNo}/refund-confirmation'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/refunds'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNo}/refund'].get").exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/orders/{orderNo}/alternative-shows'].get")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/orders/{orderNo}/refunds'].post.security[0].cookieAuth")
                        .isArray())
                .andExpect(content().string(not(containsString("paymentPassword"))))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.security[0].cookieAuth").isArray());
    }

    @Test
    void givenSeatEndpoint_whenUnauthenticatedOrAuthenticated_thenEnforceSecurityAndReturnSeatMap() throws Exception {
        String showId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM movie_show", String.class);
        mockMvc.perform(get("/api/v1/shows/{showId}/seats", showId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "seed-user")
    void givenAuthenticatedUser_whenQuerySeatMap_thenReturnStableSeatContractAndDomainErrors() throws Exception {
        Long showId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM movie_show", Long.class);
        mockMvc.perform(get("/api/v1/shows/{showId}/seats", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.showId").value(showId.toString()))
                .andExpect(jsonPath("$.data.rowCount").value(8))
                .andExpect(jsonPath("$.data.seatCount").value(80))
                .andExpect(jsonPath("$.data.availableSeatCount").value(80))
                .andExpect(jsonPath("$.data.seats", hasSize(80)))
                .andExpect(jsonPath("$.data.seats[*].seatId", everyItem(matchesPattern("\\d+"))))
                .andExpect(jsonPath("$.data.seats[0].rowNo").value("A"))
                .andExpect(jsonPath("$.data.seats[0].seatNo").value("01"));

        mockMvc.perform(get("/api/v1/shows/{showId}/seats", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(100404));

        jdbcTemplate.update("UPDATE movie_show SET status = 'STOPPED' WHERE id = ?", showId);
        try {
            mockMvc.perform(get("/api/v1/shows/{showId}/seats", showId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(204002));
        } finally {
            jdbcTemplate.update("UPDATE movie_show SET status = 'ON_SALE' WHERE id = ?", showId);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(9000001L, RoleCode.USER, 0L);
        }
    }
}
