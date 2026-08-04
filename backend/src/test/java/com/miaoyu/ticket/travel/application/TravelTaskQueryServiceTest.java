package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
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

class TravelTaskQueryServiceTest {

    @Test
    void givenAnotherUsersTask_whenQuerying_thenHideItsExistence() {
        StubRepository repository = new StubRepository(task(2L, TravelTaskStatus.PENDING));
        TravelTaskQueryService service = service(repository, 1L);

        assertThatThrownBy(() -> service.getMyTask("90001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TravelErrorCode.TASK_NOT_FOUND));
    }

    @Test
    void givenCancelledTask_whenUpdatingReminder_thenReturnCancelledCodeWithoutWrite() {
        StubRepository repository = new StubRepository(task(1L, TravelTaskStatus.CANCELLED));
        TravelTaskQueryService service = service(repository, 1L);

        assertThatThrownBy(() -> service.updateMyReminder("90001", LocalDateTime.of(2026, 8, 5, 17, 0), 0L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TravelErrorCode.TASK_CANCELLED));
        assertThat(repository.updated).isFalse();
    }

    @Test
    void givenRecentlyUpdatedReadyTask_whenRefreshing_thenRejectWithoutCallingAdviceService() {
        TravelTaskRepository.TravelTaskSnapshot recent = task(1L, TravelTaskStatus.READY);
        recent = new TravelTaskRepository.TravelTaskSnapshot(
                recent.id(), recent.taskId(), recent.userId(), recent.orderId(), recent.showId(), recent.cinemaArea(),
                recent.startAt(), recent.triggerAt(), recent.orderVersion(), recent.version(), recent.status(),
                recent.closedAt(), LocalDateTime.of(2026, 8, 4, 7, 59));
        TravelTaskQueryService service = service(new StubRepository(recent), 1L);

        assertThatThrownBy(() -> service.refreshMyAdvice("90001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(TravelErrorCode.REFRESH_TOO_FREQUENT));
    }

    private TravelTaskQueryService service(StubRepository repository, long userId) {
        CurrentUserAccessor accessor = () -> new CurrentUser(userId, RoleCode.USER, 0L);
        return new TravelTaskQueryService(repository, accessor, null,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    private TravelTaskRepository.TravelTaskSnapshot task(long userId, TravelTaskStatus status) {
        return new TravelTaskRepository.TravelTaskSnapshot(
                1L, "90001", userId, 80001L, 70001L, "西湖区",
                LocalDateTime.of(2026, 8, 5, 19, 0), LocalDateTime.of(2026, 8, 5, 17, 0),
                0L, 0L, status, status.isTerminal() ? LocalDateTime.of(2026, 8, 4, 8, 0) : null,
                LocalDateTime.of(2026, 8, 4, 8, 0));
    }

    private static final class StubRepository implements TravelTaskRepository {

        private final TravelTaskSnapshot task;
        private boolean updated;

        private StubRepository(TravelTaskSnapshot task) { this.task = task; }
        @Override public Optional<TravelTaskSnapshot> findByPaymentEventId(String eventId) {
            return Optional.empty();
        }
        @Override public Optional<TravelTaskSnapshot> findByOrderId(long orderId) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findById(long id) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
            return task.taskId().equals(taskId) && task.userId() == userId ? Optional.of(task) : Optional.empty();
        }
        @Override
        public boolean updateTriggerAt(long id, long version, LocalDateTime triggerAt, LocalDateTime updatedAt) {
            updated = true;
            return true;
        }
        @Override public void insert(NewTravelTask newTask) { throw new UnsupportedOperationException(); }
    }
}
