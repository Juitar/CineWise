package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证位置共享确认和 Provider 失败不会产生可持久化路线结果。 */
class BasicRouteServiceTest {

    @Test
    void givenSharingNotConfirmed_whenPlanning_thenDoNotCallProvider() {
        CountingProvider provider = new CountingProvider();
        BasicRouteService service = service(provider);

        assertThatThrownBy(() -> service.planMyRoute("90001", command(false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(TravelErrorCode.ROUTE_SHARING_NOT_CONFIRMED));
        org.assertj.core.api.Assertions.assertThat(provider.calls).isZero();
    }

    @Test
    void givenProviderUnavailable_whenPlanning_thenReturnStableUnavailableCode() {
        assertThatThrownBy(() -> service(new CountingProvider()).planMyRoute("90001", command(true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE));
    }

    @Test
    void givenProviderThrows_whenPlanning_thenReturnStableCodeWithoutOrigin() {
        BasicRouteService service = service(new ThrowingProvider());

        assertThatThrownBy(() -> service.planMyRoute("90001", command(true)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                            .isEqualTo(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE);
                    org.assertj.core.api.Assertions.assertThat(error.getMessage()).doesNotContain("西湖文化广场");
                });
    }

    private BasicRouteService service(BasicRouteProvider provider) {
        CurrentUserAccessor user = () -> new CurrentUser(1L, RoleCode.USER, 0L);
        return new BasicRouteService(new StubRepository(), user, provider,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    private BasicRouteCommand command(boolean confirmed) {
        return new BasicRouteCommand(BasicRouteCommand.OriginType.MANUAL, "西湖文化广场", "TRANSIT", confirmed);
    }

    private static final class CountingProvider implements BasicRouteProvider {
        private int calls;
        @Override
        public Optional<BasicRouteResult> plan(
                String origin, String area, String mode, java.time.OffsetDateTime at) {
            calls++;
            return Optional.empty();
        }
    }

    private static final class ThrowingProvider implements BasicRouteProvider {
        @Override
        public Optional<BasicRouteResult> plan(
                String origin, String area, String mode, java.time.OffsetDateTime at) {
            throw new IllegalStateException("地图服务超时：" + origin);
        }
    }

    private static final class StubRepository implements TravelTaskRepository {
        private final TravelTaskSnapshot task = new TravelTaskSnapshot(1L, "90001", 1L, 2L, 3L, "西湖区",
                LocalDateTime.of(2026, 8, 5, 19, 0), LocalDateTime.of(2026, 8, 5, 17, 0), 0L, 0L,
                TravelTaskStatus.READY, null, LocalDateTime.of(2026, 8, 4, 0, 0));
        @Override public Optional<TravelTaskSnapshot> findByPaymentEventId(String eventId) {
            return Optional.empty();
        }
        @Override public Optional<TravelTaskSnapshot> findByOrderId(long orderId) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findById(long id) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
            return Optional.of(task);
        }
        @Override public boolean updateTriggerAt(long id, long version, LocalDateTime at, LocalDateTime updatedAt) {
            return false;
        }
        @Override public void insert(NewTravelTask task) { throw new UnsupportedOperationException(); }
    }
}
