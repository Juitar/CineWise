package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 验证场次电影上下文缺失时，支付成功和事件发布仍保持可用。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(PaymentIntegrationTest.PaymentTestConfiguration.class)
class PaymentMovieIdNullableIntegrationTest {

    private static final long USER_ID = 9_400_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentIntegrationTest.PaymentCurrentUserAccessor currentUserAccessor;

    @Autowired
    private PaymentIntegrationTest.PaymentEventProbe paymentEventProbe;

    @MockitoBean
    private TravelEventContextResolver travelEventContextResolver;

    @BeforeEach
    void resetTransactionData() {
        jdbcTemplate.update("DELETE FROM ticket_order_operation");
        jdbcTemplate.update("DELETE FROM refund_request");
        jdbcTemplate.update("DELETE FROM electronic_ticket");
        jdbcTemplate.update("DELETE FROM mock_payment");
        jdbcTemplate.update("DELETE FROM ticket_order_seat");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("""
                UPDATE show_seat
                   SET status = 'AVAILABLE',
                       lock_order_no = NULL,
                       lock_expire_time = NULL,
                       version = 0
                """);
        currentUserAccessor.useUser(USER_ID);
        reset(travelEventContextResolver);
        paymentEventProbe.reset();
    }

    @Test
    void givenMissingMovieId_whenPay_thenPaymentSucceedsAndPublishesNullableMovieId() {
        ShowSeats showSeats = findFutureShowSeats();
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                showSeats.showId(),
                List.of(showSeats.seatId()),
                "payment-null-movie-create-key",
                "payment-null-movie-order-key"));
        long cinemaId = findCinemaId(showSeats.showId());
        LocalDateTime startTime = findStartTime(showSeats.showId());
        when(travelEventContextResolver.resolve(any())).thenReturn(Optional.of(
                new TravelEventContextResolver.TravelEventContext(
                        showSeats.showId(), null, cinemaId, "区域", startTime)));

        PaymentView paid = paymentApplicationService.pay(order.orderNo(), "payment-null-movie-key");

        assertThat(paid.orderStatus()).isEqualTo(com.miaoyu.ticket.order.domain.OrderStatus.PAID);
        assertThat(paid.paymentStatus()).isEqualTo(com.miaoyu.ticket.order.domain.PaymentStatus.SUCCESS);
        assertThat(paymentEventProbe.events()).singleElement().satisfies(event -> {
            assertThat(event.showId()).isEqualTo(Long.toString(showSeats.showId()));
            assertThat(event.movieId()).isNull();
        });
    }

    private ShowSeats findFutureShowSeats() {
        Long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > '2026-08-02 08:00:00'
                 ORDER BY start_time, id
                 LIMIT 1
                """, Long.class);
        Long seatId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM show_seat
                 WHERE show_id = ?
                   AND status = 'AVAILABLE'
                 ORDER BY id
                 LIMIT 1
                """, Long.class, showId);
        return new ShowSeats(showId, seatId);
    }

    private long findCinemaId(long showId) {
        return jdbcTemplate.queryForObject(
                "SELECT cinema_id FROM movie_show WHERE id = ?", Long.class, showId);
    }

    private LocalDateTime findStartTime(long showId) {
        Timestamp startTime = jdbcTemplate.queryForObject(
                "SELECT start_time FROM movie_show WHERE id = ?", Timestamp.class, showId);
        return startTime.toLocalDateTime();
    }

    private record ShowSeats(long showId, long seatId) {
    }
}
