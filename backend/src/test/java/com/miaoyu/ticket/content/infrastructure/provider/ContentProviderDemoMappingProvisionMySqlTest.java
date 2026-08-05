package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentSeedApplicationService;
import com.miaoyu.ticket.content.application.ContentSeedCatalog;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 为真实 Provider 验证准备 D 自己的 Demo 内容主键映射。
 *
 * <p>该测试默认跳过，只有受控环境显式设置开关后才会连接 MySQL。它只调用内容种子服务，不调用 A 的
 * {@code TicketingSeedApplicationService}，因此不会创建影厅、场次、订单或座位。</p>
 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_CONTENT_PROVIDER_PROVISION", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "cinewise.seed.enabled=false",
    "cinewise.scheduling.enabled=false",
    "cinewise.content.netstart.enabled=false",
    "management.health.redis.enabled=false",
    "cinewise.auth.jwt-secret=content-provider-provision-jwt-secret-at-least-32-bytes",
    "cinewise.auth.audit-hash-secret=content-provider-provision-audit-secret-at-least-32-bytes"
})
class ContentProviderDemoMappingProvisionMySqlTest {

    private static final String REQUIRED_DATABASE = "cinewise_content_provider_check";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContentSeedApplicationService contentSeedApplicationService;

    @Autowired
    private ContentQueryService contentQueryService;

    @BeforeEach
    void requireDedicatedProviderTestDatabase() {
        // 先确认数据库名再写入，避免环境变量配错时把固定 Demo 映射写到日常联调库。
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("Demo 映射准备只允许使用独立 Provider 测试库")
                .isEqualTo(REQUIRED_DATABASE);
    }

    @Test
    void givenEmptyProviderTestDatabase_whenProvisionDemoMappings_thenOnlyContentRowsSupportMockFallback() {
        ContentSeedCatalog catalog = contentSeedApplicationService.ensureFixedSeed();

        // 幂等准备后的业务 ID 必须可被 Demo Provider 查回，否则 Provider 关闭时公开页面仍会得到 404。
        ContentResult<java.util.List<? extends ContentItem>> movies = contentQueryService.query(
                new ContentQuery(ContentResourceType.MOVIE, null, null, null));
        ContentResult<java.util.List<? extends ContentItem>> cinemas = contentQueryService.query(
                new ContentQuery(ContentResourceType.CINEMA, null, "330100", null));

        assertThat(catalog.movies()).isNotEmpty();
        assertThat(catalog.cinemas()).isNotEmpty();
        assertThat(movies.source().type()).isEqualTo(ContentSourceType.MOCK);
        assertThat(movies.data()).isNotEmpty()
                .allSatisfy(item -> assertThat(((MovieContent) item).movieId()).isPositive());
        assertThat(cinemas.source().type()).isEqualTo(ContentSourceType.MOCK);
        assertThat(cinemas.data()).isNotEmpty()
                .allSatisfy(item -> assertThat(((CinemaContent) item).cinemaId()).isPositive());
    }
}
