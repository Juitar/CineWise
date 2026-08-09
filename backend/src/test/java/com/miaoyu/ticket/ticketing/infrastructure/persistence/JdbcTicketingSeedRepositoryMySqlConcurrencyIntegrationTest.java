package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用隔离 MySQL 验证演示排期清理不会与锁座事务竞争删除座位。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_CONCURRENCY_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "spring.flyway.enabled=true",
    "management.health.redis.enabled=false"
})
class JdbcTicketingSeedRepositoryMySqlConcurrencyIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long SHOW_ID = 9_997_000_001L;
    private static final long SEAT_ID = 9_997_000_002L;

    @Autowired
    private JdbcTicketingSeedRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareFixture() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("清理并发测试只允许操作一次性隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        deleteFixture();
        LocalDateTime endedAt = LocalDateTime.of(2000, 1, 1, 10, 0);
        jdbcTemplate.update("""
                INSERT INTO movie_show
                    (id, movie_id, cinema_id, auditorium_id, start_time, end_time, language_version,
                     base_price, data_type, source, status, version, create_time, update_time)
                VALUES (?, 1, 1, 1, ?, ?, '国语 2D', ?, 'MOCK', 'demo-seed', 'ON_SALE', 0, ?, ?)
                """, SHOW_ID, Timestamp.valueOf(endedAt.minusHours(2)), Timestamp.valueOf(endedAt),
                new BigDecimal("39.90"), Timestamp.valueOf(endedAt), Timestamp.valueOf(endedAt));
        jdbcTemplate.update("""
                INSERT INTO show_seat
                    (id, show_id, row_no, seat_no, seat_label, status, lock_order_no, lock_expire_time,
                     version, create_time, update_time)
                VALUES (?, ?, 'A', '01', 'A排1座', 'AVAILABLE', NULL, NULL, 0, ?, ?)
                """, SEAT_ID, SHOW_ID, Timestamp.valueOf(endedAt), Timestamp.valueOf(endedAt));
    }

    @AfterEach
    void cleanFixture() {
        deleteFixture();
    }

    @Test
    void givenSeatIsLockedAfterCleanupReadsCandidate_whenCleanupRechecks_thenItKeepsTheShowAndSeat() throws Exception {
        CountDownLatch seatLocked = new CountDownLatch(1);
        CountDownLatch releaseSeatLock = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> locker = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT id FROM show_seat WHERE id = ? FOR UPDATE", Long.class, SEAT_ID);
                seatLocked.countDown();
                await(releaseSeatLock);
                jdbcTemplate.update("""
                        UPDATE show_seat
                           SET status = 'LOCKED',
                               lock_order_no = 'cleanup-race-order',
                               lock_expire_time = ?
                         WHERE id = ?
                        """, Timestamp.valueOf(LocalDateTime.of(2000, 1, 1, 11, 0)), SEAT_ID);
            }));
            assertThat(seatLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> cleanup = executor.submit(() -> transaction.execute(status ->
                    repository.deleteExpiredUnreferencedDemoShows(LocalDateTime.of(2000, 1, 1, 12, 0), 1)));
            awaitUntilBlocked(cleanup);
            releaseSeatLock.countDown();

            assertThat(cleanup.get(10, TimeUnit.SECONDS)).isZero();
            locker.get(10, TimeUnit.SECONDS);
            assertThat(count("movie_show")).isOne();
            assertThat(count("show_seat")).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM show_seat WHERE id = ?", String.class, SEAT_ID)).isEqualTo("LOCKED");
        } finally {
            releaseSeatLock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitUntilBlocked(Future<Integer> cleanup) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!cleanup.isDone()) {
                Thread.sleep(50);
                if (!cleanup.isDone()) {
                    return;
                }
            }
        }
        throw new AssertionError("清理任务未在座位锁上等待，无法验证并发复核");
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE "
                + ("movie_show".equals(tableName) ? "id" : "show_id") + " = ?", Long.class, SHOW_ID);
    }

    private void deleteFixture() {
        jdbcTemplate.update("DELETE FROM show_seat WHERE show_id = ?", SHOW_ID);
        jdbcTemplate.update("DELETE FROM movie_show WHERE id = ?", SHOW_ID);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("并发测试等待超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试线程被中断", exception);
        }
    }
}
