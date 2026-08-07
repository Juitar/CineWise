package com.miaoyu.ticket.ticketing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import com.miaoyu.ticket.content.application.ContentSeedCatalog;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@AutoConfigureMockMvc
@Import(AvailableMoviesUnavailableIntegrationTest.UnavailableContentConfiguration.class)
class AvailableMoviesUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenSchedulesButContentCatalogUnavailable_whenQueryAvailableMovies_thenReturn503() throws Exception {
        String cinemaId = jdbcTemplate.queryForObject("SELECT MIN(cinema_id) FROM movie_show", String.class);

        mockMvc.perform(get("/api/v1/shows/available-movies").param("cinemaId", cinemaId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(303004))
                .andExpect(jsonPath("$.message").value("内容数据暂不可用"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnavailableContentConfiguration {

        @Bean
        @Primary
        ContentPurchaseQueryPort unavailableContentPurchaseQueryPort() {
            return new ContentPurchaseQueryPort() {
                @Override
                public Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds) {
                    throw new BusinessException(UnavailableContentError.DATA_UNAVAILABLE);
                }

                @Override
                public Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog() {
                    return Optional.empty();
                }

                @Override
                public DemoPurchaseCatalog findLiveDemoPurchaseCatalog(String cityCode) {
                    throw new BusinessException(ContentSummaryQueryPort.ContentSummaryErrorCode.DATA_UNAVAILABLE);
                }
            };
        }
    }

    private enum UnavailableContentError implements ErrorCode {
        DATA_UNAVAILABLE;

        @Override
        public int code() {
            return 303004;
        }

        @Override
        public String message() {
            return "内容数据暂不可用";
        }

        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
    }
}
