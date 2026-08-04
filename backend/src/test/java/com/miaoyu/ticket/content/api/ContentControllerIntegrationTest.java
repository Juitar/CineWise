package com.miaoyu.ticket.content.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.content.application.ContentCachePort;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {"cinewise.seed.enabled=true", "cinewise.seed.fixed-value=20260802"})
@AutoConfigureMockMvc
@Import(ContentControllerIntegrationTest.FixedClockConfiguration.class)
class ContentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContentCachePort contentCachePort;

    /**
     * 公共内容页面不应依赖登录；列表同时校验 C 要求的分页、Demo 标识和 ISO 偏移时间。
     */
    @Test
    void shouldExposePublicMovieAndCinemaListsWithCContract() throws Exception {
        mockMvc.perform(get("/api/v1/movies")
                        .param("keyword", "  星河  ")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].movieId").isString())
                .andExpect(jsonPath("$.data.records[0].genres").isArray())
                .andExpect(jsonPath("$.data.source").value("DEMO_CONTENT"))
                .andExpect(jsonPath("$.data.sourceType").value("MOCK"))
                .andExpect(jsonPath("$.data.dataTime").value("2026-08-03T08:00:00+08:00"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-03T14:00:00+08:00"))
                .andExpect(jsonPath("$.data.isExpired").value(false))
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.fallbackType").value("MOCK"))
                .andExpect(jsonPath("$.data.expired").doesNotExist());

        mockMvc.perform(get("/api/v1/cinemas")
                        .param("location", "330100")
                        .param("keyword", "江南大道"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].cinemaId").isString());
    }

    /** 合法筛选没有命中仍代表 Demo 来源可用，C 的列表页应进入空状态而不是失败页。 */
    @Test
    void shouldReturnEmptyMoviePageWhenKeywordDoesNotMatchAnyContent() throws Exception {
        mockMvc.perform(get("/api/v1/movies")
                        .param("keyword", "NO_SUCH_CINEWISE_MOVIE")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.source").value("DEMO_CONTENT"))
                .andExpect(jsonPath("$.data.sourceType").value("MOCK"))
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.fallbackType").value("MOCK"));
    }

    /** 同步后的 LIVE 内容必须带本库业务 ID，公开列表不能把来源 ID 或空 ID 当成详情资源。 */
    @Test
    void shouldExposeSynchronizedLiveMovieUsingItsInternalBusinessId() throws Exception {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, null, null, "同步验证影片");
        ContentResult<List<? extends ContentItem>> synchronizedMovie = new ContentResult<>(List.of(
                new MovieContent(2_001L, "netstart-movie-1", "同步验证影片", "[\"剧情\"]", 90,
                        new BigDecimal("8.0"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE),
                LocalDateTime.of(2026, 8, 3, 8, 0), LocalDateTime.of(2026, 8, 3, 14, 0), false, false, null);
        contentCachePort.save(query, synchronizedMovie);

        mockMvc.perform(get("/api/v1/movies").param("keyword", "同步验证影片"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].movieId").value("2001"))
                .andExpect(jsonPath("$.data.source").value("NETSTART_MAOYAN"))
                .andExpect(jsonPath("$.data.sourceType").value("LIVE"));
    }

    /** 详情必须保留来源信息，但不能把内部坐标、距离字段泄漏给前端。 */
    @Test
    void shouldExposeDetailsWithoutTicketingOrRouteFields() throws Exception {
        mockMvc.perform(get("/api/v1/movies/{movieId}", firstMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posterUrl").isEmpty())
                .andExpect(jsonPath("$.data.summary").isEmpty())
                .andExpect(jsonPath("$.data.isExpired").value(false))
                .andExpect(jsonPath("$.data.expired").doesNotExist());

        mockMvc.perform(get("/api/v1/cinemas/{cinemaId}", firstCinemaId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.longitude").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist())
                .andExpect(jsonPath("$.data.distance").doesNotExist())
                .andExpect(jsonPath("$.data.travelMinutes").doesNotExist());
    }

    /** 参数错误与不存在内容的错误码必须稳定，供 C 的页面提示和重试逻辑使用。 */
    @Test
    void shouldRejectInvalidParametersAndReturnNotFoundForUnknownDetails() throws Exception {
        mockMvc.perform(get("/api/v1/movies/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));
        mockMvc.perform(get("/api/v1/cinemas").param("location", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));
        mockMvc.perform(get("/api/v1/movies").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));
        mockMvc.perform(get("/api/v1/movies/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(100404));
    }

    /** OpenAPI 必须同步四个接口与 isExpired 字段，不能只靠控制器存在来判断。 */
    @Test
    void shouldPublishContentRestContractInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/movies'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/movies/{movieId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cinemas'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cinemas/{cinemaId}'].get").exists())
                .andExpect(jsonPath("$.components.schemas.MovieDetailResponse.properties.isExpired").exists())
                .andExpect(jsonPath("$.components.schemas.MovieDetailResponse.properties.expired").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MovieDetailResponse.properties.dataTime.format")
                        .value("date-time"));
    }

    /** 固定种子使用雪花 ID，测试从当前 H2 种子读取实际 ID，不能把环境无关 ID 写死。 */
    private long firstMovieId() {
        return jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM movie WHERE source = 'demo-seed'", Long.class);
    }

    /** 同上，影院详情也必须走当前数据库中的实际业务 ID。 */
    private long firstCinemaId() {
        return jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM cinema WHERE source = 'demo-seed'", Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        /** 固定业务时钟让 Demo 的 dataTime/expiresAt 断言不依赖测试运行时刻。 */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
