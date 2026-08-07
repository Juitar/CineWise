package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 验证结束失效任务只迁移电子票，并依赖数据库条件更新抵御重复和并发触发。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(ShowEndTicketInvalidationIntegrationTest.FixedClockConfiguration.class)
class ShowEndTicketInvalidationIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final String TEST_SOURCE = "show-end-invalidation-test";
    private static final AtomicLong NEXT_TEST_ID = new AtomicLong(9_700_000_000L);

    @Autowired
    private ShowEndTicketInvalidationService invalidationService;

    @Autowired
    private ShowEndTicketInvalidationTransaction invalidationTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFixtures() {
        Assumptions.assumeTrue(hasInvalidationReasonColumn(),
                "固定H2测试结构只执行到V009；V022电子票失效原因与索引由MySQL集成环境验证");
        jdbcTemplate.update("DELETE FROM electronic_ticket WHERE ticket_code LIKE 'END-TEST-%'");
        jdbcTemplate.update("DELETE FROM ticket_order WHERE order_no LIKE 'END-TEST-%'");
        jdbcTemplate.update("DELETE FROM show_seat WHERE lock_order_no LIKE 'END-TEST-%'");
        jdbcTemplate.update("DELETE FROM movie_show WHERE source = ?", TEST_SOURCE);
    }

    private boolean hasInvalidationReasonColumn() {
        try {
            jdbcTemplate.query("SELECT invalidation_reason FROM electronic_ticket WHERE 1 = 0",
                    (resultSet, rowNumber) -> null);
            return true;
        } catch (org.springframework.dao.DataAccessException exception) {
            return false;
        }
    }

    @Test
    void givenEndBoundaryAndFutureShow_whenInvalidating_thenOnlyEndedTicketChangesAndOrderSeatStay() {
        TicketFixture ended = createPaidTicket(NOW);
        TicketFixture future = createPaidTicket(NOW.plusSeconds(1));

        ShowEndTicketInvalidationReport report = invalidationService.invalidateShowEndedTickets();

        assertThat(report.scannedCount()).isEqualTo(1);
        assertThat(report.invalidatedCount()).isEqualTo(1);
        assertThat(ticketStatus(ended.ticketId())).isEqualTo("INVALIDATED");
        assertThat(ticketInvalidatedAt(ended.ticketId())).isEqualTo(NOW);
        assertThat(ticketVersion(ended.ticketId())).isEqualTo(1);
        assertThat(ticketStatus(future.ticketId())).isEqualTo("VALID");
        assertThat(orderStatus(ended.orderId())).isEqualTo("PAID");
        assertThat(seatStatus(ended.seatId())).isEqualTo("SOLD");
        assertThat(refundCount(ended.orderId())).isZero();
    }

    @Test
    void givenRefundedAndPreviouslyInvalidatedTickets_whenInvalidating_thenTheyAreNotRewritten() {
        TicketFixture refunded = createPaidTicket(NOW.minusMinutes(1));
        TicketFixture invalidated = createPaidTicket(NOW.minusMinutes(1));
        jdbcTemplate.update(
                "UPDATE electronic_ticket SET status = 'REFUNDED', invalidated_time = ?, version = 3 WHERE id = ?",
                NOW.minusMinutes(2),
                refunded.ticketId());
        jdbcTemplate.update(
                "UPDATE electronic_ticket SET status = 'INVALIDATED', invalidated_time = ?, version = 5 WHERE id = ?",
                NOW.minusMinutes(2),
                invalidated.ticketId());

        ShowEndTicketInvalidationReport report = invalidationService.invalidateShowEndedTickets();

        assertThat(report.scannedCount()).isZero();
        assertThat(ticketStatus(refunded.ticketId())).isEqualTo("REFUNDED");
        assertThat(ticketVersion(refunded.ticketId())).isEqualTo(3);
        assertThat(ticketStatus(invalidated.ticketId())).isEqualTo("INVALIDATED");
        assertThat(ticketVersion(invalidated.ticketId())).isEqualTo(5);
    }

    @Test
    void givenTwoConcurrentTransactions_whenInvalidatingSameTicket_thenOnlyOneConditionalUpdateWins()
            throws Exception {
        TicketFixture fixture = createPaidTicket(NOW.minusMinutes(1));
        ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate candidate =
                new ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate(fixture.ticketId(), 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> invalidateAfterSignal(start, candidate)),
                    executor.submit(() -> invalidateAfterSignal(start, candidate)));
            start.countDown();
            assertThat(results.stream().map(this::getResult).toList())
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ticketStatus(fixture.ticketId())).isEqualTo("INVALIDATED");
        assertThat(ticketVersion(fixture.ticketId())).isEqualTo(1);
        assertThat(orderStatus(fixture.orderId())).isEqualTo("PAID");
        assertThat(seatStatus(fixture.seatId())).isEqualTo("SOLD");
    }

    private boolean invalidateAfterSignal(
            CountDownLatch start,
            ElectronicTicketLifecycleRepository.ShowEndedTicketCandidate candidate) throws InterruptedException {
        start.await();
        return invalidationTransaction.invalidate(candidate, NOW);
    }

    private boolean getResult(Future<Boolean> result) {
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("并发电子票失效任务未在时限内完成", exception);
        }
    }

    /** 每个夹具独占场次、订单、座位和票号，避免测试靠外部订单或支付数据。 */
    private TicketFixture createPaidTicket(LocalDateTime showEndTime) {
        long ticketId = nextBusinessId();
        long orderId = nextBusinessId();
        long showId = nextBusinessId();
        long seatId = nextBusinessId();
        String orderNo = "END-TEST-" + orderId;
        long auditoriumId = jdbcTemplate.queryForObject(
                "SELECT id FROM auditorium ORDER BY id LIMIT 1",
                Long.class);
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, '2D国语', ?, 'MOCK', ?, 'ON_SALE', 0, ?, ?)
                """,
                showId,
                8_800_001L,
                8_800_002L,
                auditoriumId,
                NOW.minusHours(2).minusSeconds(showId % 1_000),
                showEndTime,
                new BigDecimal("39.90"),
                TEST_SOURCE,
                NOW,
                NOW);
        jdbcTemplate.update("""
                INSERT INTO show_seat (
                    id, show_id, row_no, seat_no, seat_label, status,
                    lock_order_no, lock_expire_time, version, create_time, update_time
                ) VALUES (?, ?, '1', '1', '1排1座', 'SOLD', ?, NULL, 0, ?, ?)
                """, seatId, showId, orderNo, NOW, NOW);
        jdbcTemplate.update("""
                INSERT INTO ticket_order (
                    id, order_no, user_id, show_id, ticket_count, unit_price, total_amount,
                    status, expire_time, client_request_id, idempotency_key, version,
                    paid_time, cancelled_time, refunded_time, create_time, update_time
                ) VALUES (?, ?, ?, ?, 1, ?, ?, 'PAID', ?, ?, ?, 0, ?, NULL, NULL, ?, ?)
                """,
                orderId,
                orderNo,
                9_200_001L,
                showId,
                new BigDecimal("39.90"),
                new BigDecimal("39.90"),
                NOW.minusMinutes(15),
                "request-" + orderId,
                "key-" + orderId,
                NOW.minusMinutes(10),
                NOW.minusMinutes(10),
                NOW.minusMinutes(10));
        jdbcTemplate.update("""
                INSERT INTO electronic_ticket (
                    id, ticket_code, order_id, user_id, status, qr_payload,
                    issued_time, invalidated_time, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, 'VALID', ?, ?, NULL, 0, ?, ?)
                """,
                ticketId,
                "END-TEST-" + ticketId,
                orderId,
                9_200_001L,
                "cinewise:ticket:" + ticketId,
                NOW.minusMinutes(10),
                NOW.minusMinutes(10),
                NOW.minusMinutes(10));
        return new TicketFixture(ticketId, orderId, seatId);
    }

    private long nextBusinessId() {
        return NEXT_TEST_ID.incrementAndGet();
    }

    private String ticketStatus(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM electronic_ticket WHERE id = ?", String.class, ticketId);
    }

    private LocalDateTime ticketInvalidatedAt(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT invalidated_time FROM electronic_ticket WHERE id = ?", LocalDateTime.class, ticketId);
    }

    private int ticketVersion(long ticketId) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT version FROM electronic_ticket WHERE id = ?", Integer.class, ticketId);
        return version == null ? -1 : version;
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM ticket_order WHERE id = ?", String.class, orderId);
    }

    private String seatStatus(long seatId) {
        return jdbcTemplate.queryForObject("SELECT status FROM show_seat WHERE id = ?", String.class, seatId);
    }

    private long refundCount(long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_request WHERE order_id = ?", Long.class, orderId);
        return count == null ? 0L : count;
    }

    private record TicketFixture(long ticketId, long orderId, long seatId) {
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
