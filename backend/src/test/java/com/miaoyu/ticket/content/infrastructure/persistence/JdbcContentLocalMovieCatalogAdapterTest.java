package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcContentLocalMovieCatalogAdapterTest {

    @Test
    void givenLocalV014Movies_whenQuery_thenItFiltersAndOrdersByReleaseDateWithoutProvider() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        jdbc.execute("""
                CREATE TABLE movie (
                    id BIGINT PRIMARY KEY, source_movie_id VARCHAR(128), title VARCHAR(255), genres_json VARCHAR(255),
                    duration_minutes INT, rating DECIMAL(3,1), poster_url VARCHAR(2048), summary VARCHAR(2000),
                    release_status VARCHAR(16), release_date DATE, source_type VARCHAR(16), source VARCHAR(64),
                    data_time TIMESTAMP, expires_at TIMESTAMP, deleted_at TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO movie VALUES (1, 'm1', '已上映较早', '[\"剧情\"]', 100, 8.0, NULL, NULL,
                    'NOW_SHOWING', DATE '2026-08-01', 'LIVE', 'NETSTART_MAOYAN',
                    TIMESTAMP '2026-08-06 10:00:00', TIMESTAMP '2026-08-07 10:00:00', NULL)
                """);
        jdbc.update("""
                INSERT INTO movie VALUES (2, 'm2', '已上映较新', '[\"动作\"]', 110, 8.5, NULL, NULL,
                    'NOW_SHOWING', DATE '2026-08-05', 'LIVE', 'NETSTART_MAOYAN',
                    TIMESTAMP '2026-08-06 11:00:00', TIMESTAMP '2026-08-07 11:00:00', NULL)
                """);
        jdbc.update("""
                INSERT INTO movie VALUES (3, 'm3', '待映影片', '[\"动画\"]', 90, 7.5, NULL, NULL,
                    'COMING_SOON', DATE '2026-09-01', 'LIVE', 'NETSTART_MAOYAN',
                    TIMESTAMP '2026-08-06 11:00:00', TIMESTAMP '2026-08-07 11:00:00', NULL)
                """);

        JdbcContentLocalMovieCatalogAdapter adapter = new JdbcContentLocalMovieCatalogAdapter(jdbc,
                Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC));

        var result = adapter.findMovies("已上映", "NOW_SHOWING").orElseThrow();

        assertThat(result.data()).extracting(item -> ((com.miaoyu.ticket.content.domain.MovieContent) item).movieId())
                .containsExactly(2L, 1L);
        assertThat(result.source().name()).isEqualTo("NETSTART_MAOYAN");
    }

    private DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:local-movie-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}
