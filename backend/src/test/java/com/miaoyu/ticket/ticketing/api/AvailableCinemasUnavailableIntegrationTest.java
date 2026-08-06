package com.miaoyu.ticket.ticketing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
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

/** 内容目录整体不可读时，HTTP 层必须保留 D 的 303004 而不是伪造空影院页。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"cinewise.seed.enabled=true", "cinewise.seed.fixed-value=20260802"})
@AutoConfigureMockMvc
@Import(AvailableCinemasUnavailableIntegrationTest.UnavailableContentConfiguration.class)
class AvailableCinemasUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser
    void givenSaleableCinemaButContentUnavailable_whenQuery_thenReturn303004And503() throws Exception {
        String movieId = jdbcTemplate.queryForObject("SELECT MIN(movie_id) FROM movie_show", String.class);

        mockMvc.perform(get("/api/v1/shows/available-cinemas").param("movieId", movieId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(303004))
                .andExpect(jsonPath("$.message").value("内容数据暂不可用"))
                .andExpect(content().string(not(containsString("\"data\""))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnavailableContentConfiguration {

        @Bean
        @Primary
        ContentSummaryQueryPort unavailableContentSummaryQueryPort() {
            return cinemaIds -> {
                throw new BusinessException(ContentSummaryQueryPort.ContentSummaryErrorCode.DATA_UNAVAILABLE);
            };
        }
    }
}
