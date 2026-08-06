package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import com.miaoyu.ticket.content.application.ContentSeedCatalog;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.order.application.TravelOrderSummaryQueryPort;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.List;
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

    @Test
    void givenGeneratedAdvice_whenOwnerReads_thenReturnDisplayContent() {
        StubRepository repository = new StubRepository(task(1L, TravelTaskStatus.READY));
        TravelTaskQueryService service = service(repository, new StubAdviceRepository(snapshot()));

        TravelTaskQueryService.TravelAdviceSummary advice = service.getMyAdviceSummary("90001");

        assertThat(advice.available()).isTrue();
        assertThat(advice.adviceJson()).contains("交通");
        assertThat(advice.weatherJson()).contains("多云");
    }

    @Test
    void givenCancelledTaskAndAvailablePublicSummaries_whenReadingDetails_thenKeepCancelledStatus() {
        StubRepository repository = new StubRepository(task(1L, TravelTaskStatus.CANCELLED));
        TravelTaskQueryService service = detailedService(
                repository, availableOrder(), availableMovies(), availableCinemas());

        TravelTaskQueryService.TravelTaskDetails details = service.getMyTaskDetails("90001");

        assertThat(details.status()).isEqualTo(TravelTaskStatus.CANCELLED);
        assertThat(details.order().orderNo()).isEqualTo("ORD-80001");
        assertThat(details.movie().title()).isEqualTo("测试影片");
        assertThat(details.cinema().address()).isEqualTo("测试路 1 号");
    }

    @Test
    void givenContentSummaryUnavailable_whenReadingDetails_thenHideDependencyErrorBehindDCode() {
        StubRepository repository = new StubRepository(task(1L, TravelTaskStatus.READY));
        ContentPurchaseQueryPort unavailableMovies = new ContentPurchaseQueryPort() {
            @Override public Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds) {
                throw new BusinessException(TravelErrorCode.TASK_NOT_FOUND);
            }
            @Override public Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog() { return Optional.empty(); }
        };
        TravelTaskQueryService service = detailedService(
                repository, availableOrder(), unavailableMovies, availableCinemas());

        assertThatThrownBy(() -> service.getMyTaskDetails("90001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(TravelErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    private TravelTaskQueryService detailedService(
            StubRepository repository, TravelOrderSummaryQueryPort orderPort, ContentPurchaseQueryPort moviePort,
            ContentSummaryQueryPort cinemaPort) {
        CurrentUserAccessor accessor = () -> new CurrentUser(1L, RoleCode.USER, 0L);
        return new TravelTaskQueryService(repository, accessor, new StubAdviceRepository(null), null,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC), orderPort, moviePort, cinemaPort);
    }

    private TravelOrderSummaryQueryPort availableOrder() {
        return orderId -> new TravelOrderSummaryQueryPort.TravelOrderSummary(
                orderId, "ORD-80001", "70001", "10001", "20001",
                java.time.OffsetDateTime.parse("2026-08-05T19:00:00+08:00"));
    }

    private ContentPurchaseQueryPort availableMovies() {
        return new ContentPurchaseQueryPort() {
            @Override public Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds) {
                return Map.of(10001L, new MovieSummary(10001L, "测试影片", null,
                        "DEMO_CONTENT_V1", LocalDateTime.of(2026, 8, 4, 8, 0)));
            }
            @Override public Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog() { return Optional.empty(); }
        };
    }

    private ContentSummaryQueryPort availableCinemas() {
        return cinemaIds -> new ContentSummaryQueryPort.CinemaSummaryBatch(List.of(
                new ContentSummaryQueryPort.CinemaSummary(20001L, "测试影院", null, "测试路 1 号",
                        "DEMO_CONTENT_V1", LocalDateTime.of(2026, 8, 4, 8, 0),
                        LocalDateTime.of(2026, 8, 4, 8, 15), false)), Set.of());
    }

    private TravelTaskQueryService service(StubRepository repository, long userId) {
        return service(repository, new StubAdviceRepository(null), userId);
    }

    private TravelTaskQueryService service(StubRepository repository, StubAdviceRepository adviceRepository) {
        return service(repository, adviceRepository, 1L);
    }

    private TravelTaskQueryService service(
            StubRepository repository, StubAdviceRepository adviceRepository, long userId) {
        CurrentUserAccessor accessor = () -> new CurrentUser(userId, RoleCode.USER, 0L);
        return new TravelTaskQueryService(repository, accessor, adviceRepository, null,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    private TravelAdviceSnapshot snapshot() {
        return new TravelAdviceSnapshot(11L, 1L, 1L, "{\"condition\":\"多云\"}",
                "{\"transport\":\"交通建议\"}", "DEMO_WEATHER_V1", LocalDateTime.of(2026, 8, 4, 8, 0),
                LocalDateTime.of(2026, 8, 4, 8, 15), false, true, "DEMO", LocalDateTime.of(2026, 8, 4, 8, 0));
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

    private static final class StubAdviceRepository implements TravelAdviceRepository {
        private final TravelAdviceSnapshot snapshot;
        private StubAdviceRepository(TravelAdviceSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public boolean claimVersionForAdvice(long taskId, long version, LocalDateTime time) { return false; }
        @Override public void insert(TravelAdviceSnapshot item) { throw new UnsupportedOperationException(); }
        @Override public Optional<TravelAdviceSnapshot> findByTaskIdAndVersion(long taskId, long version) {
            return Optional.ofNullable(snapshot);
        }
        @Override public Optional<TravelAdviceSnapshot> findLatestByTaskId(long taskId) {
            return Optional.ofNullable(snapshot);
        }
    }
}
