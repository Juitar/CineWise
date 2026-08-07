package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.order.application.CreateOrderCommand;
import com.miaoyu.ticket.order.application.OrderApplicationService;
import com.miaoyu.ticket.order.application.OrderView;
import com.miaoyu.ticket.order.application.PaymentApplicationService;
import com.miaoyu.ticket.order.application.PaymentView;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.profile.application.ProfileBehaviorRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 A 的支付提交事件在事务提交后才进入 D 的画像监听器。
 * 测试替换记录器，只检查跨模块调用方向和失败隔离，避免把订单、支付或画像持久化细节混在同一用例中。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(ProfilePaymentEventFlowIntegrationTest.PaymentProfileTestConfiguration.class)
class ProfilePaymentEventFlowIntegrationTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final long TEST_USER_ID = 9_807_000_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProfileBehaviorRecorder profileBehaviorRecorder;

    @BeforeEach
    void resetProfileRecorder() {
        reset(profileBehaviorRecorder);
    }

    @Test
    void shouldDeliverCommittedPaymentEventToProfileAndReplayTheCapturedEvent() {
        List<PaymentSucceededEvent> receivedEvents = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            receivedEvents.add(invocation.getArgument(0));
            return new ProfileBehaviorRecorder.RecordResult(true, false);
        }).when(profileBehaviorRecorder).recordPayment(any());

        OrderView order = createOrder("profile-payment-event", "profile-payment-create-key");
        PaymentView paid = paymentApplicationService.pay(order.orderNo(), "profile-payment-pay-key");

        assertThat(paid.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(receivedEvents).singleElement().satisfies(event -> {
            assertThat(event.orderId()).isEqualTo(Long.toString(order.orderId()));
            assertThat(event.showId()).isEqualTo(Long.toString(order.showId()));
            assertThat(event.userId()).isEqualTo(Long.toString(TEST_USER_ID));
        });

        PaymentSucceededEvent capturedEvent = receivedEvents.getFirst();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> applicationEventPublisher.publishEvent(capturedEvent));

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(profileBehaviorRecorder, times(2)).recordPayment(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).containsOnly(capturedEvent);
    }

    @Test
    void shouldKeepPaidOrderWhenProfileProcessingFailsAfterCommit() {
        doThrow(new IllegalStateException("模拟画像处理失败"))
                .when(profileBehaviorRecorder)
                .recordPayment(any());

        OrderView order = createOrder("profile-payment-failure", "profile-payment-failure-create-key");
        PaymentView paid = paymentApplicationService.pay(order.orderNo(), "profile-payment-failure-pay-key");

        assertThat(paid.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentApplicationService.queryPayment(order.orderNo())).isEqualTo(paid);
        verify(profileBehaviorRecorder).recordPayment(any());
    }

    private OrderView createOrder(String clientRequestId, String idempotencyKey) {
        long showId = jdbcTemplate.queryForObject("""
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
        return orderApplicationService.createOrder(new CreateOrderCommand(
                showId, List.of(seatId), clientRequestId, idempotencyKey));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentProfileTestConfiguration {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        CurrentUserAccessor profilePaymentCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }

        @Bean
        @Primary
        ProfileBehaviorRecorder profileBehaviorRecorder() {
            return org.mockito.Mockito.mock(ProfileBehaviorRecorder.class);
        }
    }
}
