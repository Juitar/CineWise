package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcContentCityCinemaQueryAdapterTest {

    @Test
    void givenActiveAndDeletedCinemas_whenQueryCity_thenItReturnsOnlyActiveIdsInStableOrder() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:city-cinema-query;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE cinema (id BIGINT, city_name VARCHAR(64), deleted_at TIMESTAMP)");
        jdbc.update("INSERT INTO cinema VALUES (12, '杭州', NULL)");
        jdbc.update("INSERT INTO cinema VALUES (11, '杭州', NULL)");
        jdbc.update("INSERT INTO cinema VALUES (13, '杭州', TIMESTAMP '2026-08-01 00:00:00')");
        jdbc.update("INSERT INTO cinema VALUES (14, '长沙', NULL)");

        List<Long> ids = new JdbcContentCityCinemaQueryAdapter(jdbc).findActiveCinemaIds("杭州");

        assertThat(ids).containsExactly(11L, 12L);
    }
}
