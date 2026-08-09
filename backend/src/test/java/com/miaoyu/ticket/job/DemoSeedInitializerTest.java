package com.miaoyu.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.DemoContentCatalog;
import com.miaoyu.ticket.content.application.DemoContentCatalogProvider;
import com.miaoyu.ticket.content.application.ContentPersistencePort;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentSnapshotPort;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(DemoSeedInitializerTest.FixedClockConfiguration.class)
class DemoSeedInitializerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private DemoSeedInitializer initializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoContentCatalogProvider catalogProvider;

    @Autowired
    private ContentPersistencePort contentPersistencePort;

    @Autowired
    private ContentSnapshotPort contentSnapshotPort;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void givenChangshaLiveContent_whenSeedRuns_thenCreateMarkedDemoSchedulesForLiveCinema() {
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 2, 8, 0);
        long movieId = contentPersistencePort.ensureMovie(new ContentPersistencePort.MovieRow(
                9_100_001L, "live-purchase-movie", "长沙购票演示片", "[\"剧情\"]", 110,
                new BigDecimal("8.8"), null, null, null, null, ContentSourceType.LIVE, "NETSTART_MAOYAN",
                dataTime, dataTime.plusHours(6)));
        long cinemaId = contentPersistencePort.ensureCinema(new ContentPersistencePort.CinemaRow(
                9_200_001L, "live-purchase-cinema", "长沙购票演示影院", "430100", "岳麓区",
                "梅溪湖路1号", null, null, ContentSourceType.LIVE, "NETSTART_MAOYAN",
                dataTime, dataTime.plusHours(6)));
        long secondCinemaId = contentPersistencePort.ensureCinema(new ContentPersistencePort.CinemaRow(
                9_200_002L, "live-purchase-cinema-2", "长沙购票演示影院二店", "430100", "开福区",
                "芙蓉路2号", null, null, ContentSourceType.LIVE, "NETSTART_MAOYAN",
                dataTime, dataTime.plusHours(6)));
        MovieContent movie = new MovieContent(movieId, "live-purchase-movie", "长沙购票演示片",
                "[\"剧情\"]", 110, new BigDecimal("8.8"), null, null, null, null);
        CinemaContent cinema = new CinemaContent(cinemaId, "live-purchase-cinema", "长沙购票演示影院",
                "430100", "岳麓区", "梅溪湖路1号", null, null);
        ContentSource liveSource = new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE);
        ContentResult<List<? extends com.miaoyu.ticket.content.domain.ContentItem>> movieResult =
                new ContentResult<>(List.of(movie), liveSource, dataTime, dataTime.plusHours(6),
                        false, false, null);
        ContentResult<List<? extends com.miaoyu.ticket.content.domain.ContentItem>> cinemaResult =
                new ContentResult<>(List.of(cinema), liveSource, dataTime, dataTime.plusHours(6),
                        false, false, null);
        contentSnapshotPort.save(new ContentQuery(ContentResourceType.MOVIE, null, null, null), movieResult);
        contentSnapshotPort.save(new ContentQuery(ContentResourceType.MOVIE, movieId, null, null), movieResult);
        contentSnapshotPort.save(
                new ContentQuery(ContentResourceType.CINEMA, null, "430100", null), cinemaResult);

        initializer.initializeLiveDemoSchedules();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_show WHERE cinema_id = ? AND source = 'demo-seed'",
                Long.class, cinemaId)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_show WHERE cinema_id = ? AND source = 'demo-seed'",
                Long.class, secondCinemaId)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditorium WHERE cinema_id = ? AND data_type = 'MOCK'",
                Long.class, cinemaId)).isEqualTo(1L);
        // LIVE 扩展可以增加独立票务数据，但不能改变固定 Demo 种子的统计基线。
        assertSeedCounts();
        assertContentSourceIdentity();
    }

    @Test
    void givenExpiredLiveCatalog_whenSeedRuns_thenItDoesNotCreateNewMockSchedules() {
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 1, 1, 0);
        long movieId = contentPersistencePort.ensureMovie(new ContentPersistencePort.MovieRow(
                9_100_010L, "expired-live-movie", "过期影片", "[\"剧情\"]", 100,
                new BigDecimal("8.0"), null, null, null, null, ContentSourceType.LIVE, "NETSTART_MAOYAN",
                dataTime, dataTime.plusHours(1)));
        long cinemaId = contentPersistencePort.ensureCinema(new ContentPersistencePort.CinemaRow(
                9_200_010L, "expired-live-cinema", "过期影院", "430100", "岳麓区",
                "测试路10号", null, null, ContentSourceType.LIVE, "NETSTART_MAOYAN",
                dataTime, dataTime.plusHours(1)));

        initializer.initialize();

        assertThat(movieId).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_show WHERE cinema_id = ?", Long.class, cinemaId)).isZero();
    }

    @Test
    void givenLegacyDemoSeedAndLockedSeat_whenSeedRunsAgain_thenCountsStayStableAndSeatStateIsPreserved() {
        assertSeedCounts();
        assertContentSourceIdentity();
        assertScheduleWindow();

        Long seatId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM show_seat", Long.class);
        int updated = jdbcTemplate.update("""
                UPDATE show_seat
                   SET status = 'LOCKED',
                       lock_order_no = 'SEED-PRESERVE-ORDER',
                       lock_expire_time = ?,
                       version = 7
                 WHERE id = ?
                """, Timestamp.from(FIXED_INSTANT.plusSeconds(900)), seatId);
        assertThat(updated).isOne();

        // 第二次执行必须只查回既有内容和票务引用，不能覆盖已经锁定的座位交易状态。
        initializer.initialize();

        assertSeedCounts();
        assertContentSourceIdentity();
        Map<String, Object> seat = jdbcTemplate.queryForMap("""
                SELECT status, lock_order_no, version
                  FROM show_seat
                 WHERE id = ?
                """, seatId);
        assertThat(seat.get("STATUS")).isEqualTo("LOCKED");
        assertThat(seat.get("LOCK_ORDER_NO")).isEqualTo("SEED-PRESERVE-ORDER");
        assertThat(((Number) seat.get("VERSION")).intValue()).isEqualTo(7);
    }

    @Test
    void givenDemoCatalog_whenLoaded_thenItContainsOnlyStableSourceIdsAndNoDatabaseIds() {
        DemoContentCatalog catalog = catalogProvider.load();

        assertThat(catalog.version()).isEqualTo("demo-content-v1");
        assertThat(catalog.source()).isEqualTo("DEMO_CONTENT");
        assertThat(catalog.movies()).hasSize(10)
                .extracting(movie -> movie.sourceMovieId())
                .doesNotHaveDuplicates();
        assertThat(catalog.cinemas()).hasSize(4)
                .extracting(cinema -> cinema.sourceCinemaId())
                .doesNotHaveDuplicates();
    }

    private void assertSeedCounts() {
        assertThat(countBySource("movie")).isEqualTo(10);
        assertThat(countBySource("cinema")).isEqualTo(4);
        assertThat(countFixedAuditoriums()).isEqualTo(4);
        assertThat(countFixedShows()).isEqualTo(16);
        assertThat(countFixedSeats()).isEqualTo(640);
    }

    private void assertContentSourceIdentity() {
        assertThat(countBySource("movie")).isEqualTo(10);
        assertThat(countBySource("cinema")).isEqualTo(4);
    }

    private long countBySource(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE source = 'demo-seed'",
                Long.class);
    }

    private void assertScheduleWindow() {
        Timestamp firstShow = jdbcTemplate.queryForObject("""
                SELECT MIN(ms.start_time)
                  FROM movie_show ms
                  JOIN cinema c ON c.id = ms.cinema_id
                 WHERE c.source = 'demo-seed'
                   AND ms.source = 'demo-seed'
                """, Timestamp.class);
        Timestamp lastShow = jdbcTemplate.queryForObject("""
                SELECT MAX(ms.start_time)
                  FROM movie_show ms
                  JOIN cinema c ON c.id = ms.cinema_id
                 WHERE c.source = 'demo-seed'
                   AND ms.source = 'demo-seed'
                """, Timestamp.class);
        assertThat(firstShow).isEqualTo(Timestamp.valueOf("2026-08-02 09:30:00"));
        assertThat(lastShow).isEqualTo(Timestamp.valueOf("2026-08-03 19:30:00"));
    }

    /** 固定种子与 LIVE 购票扩展共用票务表，测试只统计 DEMO_CONTENT 影院的票务数据。 */
    private long countFixedAuditoriums() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM auditorium a
                  JOIN cinema c ON c.id = a.cinema_id
                 WHERE c.source = 'demo-seed'
                """, Long.class);
    }

    private long countFixedShows() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM movie_show ms
                  JOIN cinema c ON c.id = ms.cinema_id
                 WHERE c.source = 'demo-seed'
                   AND ms.source = 'demo-seed'
                """, Long.class);
    }

    private long countFixedSeats() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM show_seat ss
                  JOIN movie_show ms ON ms.id = ss.show_id
                  JOIN cinema c ON c.id = ms.cinema_id
                 WHERE c.source = 'demo-seed'
                   AND ms.source = 'demo-seed'
                """, Long.class);
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
