package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcLiveDemoPurchaseCatalogAdapterTest {
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:live-demo-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    private final JdbcLiveDemoPurchaseCatalogAdapter adapter = new JdbcLiveDemoPurchaseCatalogAdapter(jdbc,
            Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS movie");
        jdbc.execute("DROP TABLE IF EXISTS cinema");
        jdbc.execute("CREATE TABLE movie (id BIGINT, source_movie_id VARCHAR(128), duration_minutes INT, "
                + "source_type VARCHAR(16), source VARCHAR(64), data_time TIMESTAMP, expires_at TIMESTAMP, deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE cinema (id BIGINT, source_cinema_id VARCHAR(128), city_code VARCHAR(32), "
                + "source_type VARCHAR(16), source VARCHAR(64), data_time TIMESTAMP, expires_at TIMESTAMP, deleted_at TIMESTAMP)");
    }

    @Test
    void givenFreshSingleSourceContent_whenQuerying_thenItReturnsAllCinemasAndThreeMoviesById() {
        insertMovie(3, "m3", 100, "2026-08-07 10:00:00", "2026-08-08 10:00:00");
        insertMovie(1, "m1", 90, "2026-08-07 09:00:00", "2026-08-08 09:00:00");
        insertMovie(2, "m2", 110, "2026-08-07 11:00:00", "2026-08-08 11:00:00");
        insertMovie(4, "m4", 120, "2026-08-07 12:00:00", "2026-08-08 12:00:00");
        insertCinema(12, "c12", "2026-08-07 12:00:00", "2026-08-08 12:00:00");
        insertCinema(11, "c11", "2026-08-07 08:00:00", "2026-08-08 08:00:00");

        var result = adapter.findLiveCatalog("430100");

        assertThat(result.source()).isEqualTo("NETSTART_MAOYAN");
        assertThat(result.movies()).extracting(ref -> ref.movieId()).containsExactly(1L, 2L, 3L);
        assertThat(result.cinemas()).extracting(ref -> ref.cinemaId()).containsExactly(11L, 12L);
        assertThat(result.dataAt().toLocalDateTime()).isEqualTo("2026-08-07T08:00");
        assertThat(result.expiresAt().toLocalDateTime()).isEqualTo("2026-08-08T08:00");
    }

    @Test
    void givenExpiredOrDemoOnlyContent_whenQuerying_thenItReturnsEmptyCatalog() {
        jdbc.update("INSERT INTO movie VALUES (1, 'demo', 100, 'MOCK', 'DEMO_CONTENT', TIMESTAMP '2026-08-07 08:00:00', TIMESTAMP '2026-08-08 08:00:00', NULL)");
        insertCinema(1, "old", "2026-08-06 08:00:00", "2026-08-06 09:00:00");

        var result = adapter.findLiveCatalog("430100");

        assertThat(result.movies()).isEmpty();
        assertThat(result.cinemas()).isEmpty();
    }

    @Test
    void givenUnreadableStorage_whenQuerying_thenItKeeps303004() {
        jdbc.execute("DROP TABLE movie");

        assertThatThrownBy(() -> adapter.findLiveCatalog("430100"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().code())
                .isEqualTo(303004);
    }

    private void insertMovie(long id, String sourceId, int duration, String dataAt, String expiresAt) {
        jdbc.update("INSERT INTO movie VALUES (?, ?, ?, 'LIVE', 'NETSTART_MAOYAN', ?, ?, NULL)",
                id, sourceId, duration, java.sql.Timestamp.valueOf(dataAt), java.sql.Timestamp.valueOf(expiresAt));
    }

    private void insertCinema(long id, String sourceId, String dataAt, String expiresAt) {
        jdbc.update("INSERT INTO cinema VALUES (?, ?, '430100', 'LIVE', 'NETSTART_MAOYAN', ?, ?, NULL)",
                id, sourceId, java.sql.Timestamp.valueOf(dataAt), java.sql.Timestamp.valueOf(expiresAt));
    }
}
