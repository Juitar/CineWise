package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcTicketingSeedRepositoryTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:ticketing-seed-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final JdbcTicketingSeedRepository repository = new JdbcTicketingSeedRepository(jdbcTemplate);
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource));

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS show_seat");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ticket_order");
        jdbcTemplate.execute("DROP TABLE IF EXISTS movie_show");
        jdbcTemplate.execute("CREATE TABLE movie_show (id BIGINT PRIMARY KEY, source VARCHAR(32), end_time TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE show_seat (show_id BIGINT, status VARCHAR(32), lock_order_no VARCHAR(64), "
                + "lock_expire_time TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE ticket_order (id BIGINT PRIMARY KEY, show_id BIGINT)");
    }

    @Test
    void givenEarliestBatchHasOrders_whenCleaning_thenItStillDeletesLaterUnreferencedShow() {
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        for (long showId = 1; showId <= 100; showId++) {
            insertDemoShow(showId, endedAt);
            jdbcTemplate.update("INSERT INTO ticket_order (id, show_id) VALUES (?, ?)", showId, showId);
        }
        long cleanableShowId = 101;
        insertDemoShow(cleanableShowId, endedAt);
        jdbcTemplate.update("""
                INSERT INTO show_seat (show_id, status, lock_order_no, lock_expire_time)
                VALUES (?, 'AVAILABLE', NULL, NULL)
                """, cleanableShowId);

        Integer deletedCount = transactionTemplate.execute(status ->
                repository.deleteExpiredUnreferencedDemoShows(endedAt.plusHours(1), 100));

        assertThat(deletedCount).isOne();
        assertThat(countShows()).isEqualTo(100);
        assertThat(showExists(cleanableShowId)).isFalse();
        assertThat(countSeats(cleanableShowId)).isZero();
    }

    private void insertDemoShow(long showId, LocalDateTime endedAt) {
        jdbcTemplate.update("INSERT INTO movie_show (id, source, end_time) VALUES (?, 'demo-seed', ?)",
                showId, endedAt);
    }

    private long countShows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movie_show", Long.class);
    }

    private boolean showExists(long showId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movie_show WHERE id = ?", Long.class, showId);
        return count != null && count > 0;
    }

    private long countSeats(long showId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM show_seat WHERE show_id = ?", Long.class, showId);
    }
}
