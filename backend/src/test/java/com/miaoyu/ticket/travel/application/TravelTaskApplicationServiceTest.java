package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class TravelTaskApplicationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void givenSameOrderWithDifferentEventIds_whenEnsuringConcurrently_thenKeepOneTask() throws Exception {
        InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
        TravelTaskApplicationService service = service(repository);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TravelTaskSummary> first = executor.submit(() -> ensureAfterSignal(
                    service,
                    start,
                    paymentEvent("event-1", "90001")));
            Future<TravelTaskSummary> second = executor.submit(() -> ensureAfterSignal(
                    service,
                    start,
                    paymentEvent("event-2", "90001")));
            start.countDown();

            TravelTaskSummary firstResult = first.get(5, TimeUnit.SECONDS);
            TravelTaskSummary secondResult = second.get(5, TimeUnit.SECONDS);
            assertThat(firstResult.taskId()).isEqualTo(secondResult.taskId());
            assertThat(repository.count()).isOne();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void givenRepeatedEvent_whenEnsuring_thenReturnOriginalPendingTaskAndTriggerTwoHoursEarly() {
        InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
        TravelTaskApplicationService service = service(repository);
        PaymentSucceededEvent event = paymentEvent("event-repeat", "90002");

        TravelTaskSummary first = service.ensureTask(event);
        TravelTaskSummary repeated = service.ensureTask(event);
        TravelTaskRepository.TravelTaskSnapshot stored = repository.findByOrderId(90002L).orElseThrow();

        assertThat(repeated).isEqualTo(first);
        assertThat(first.status()).isEqualTo(TravelTaskStatus.PENDING);
        assertThat(repository.count()).isOne();
        assertThat(stored.triggerAt()).isEqualTo(stored.startAt().minusHours(2));
    }

    @Test
    void givenRefundArrivesBeforePayment_whenEnsuringThenPaying_thenKeepCancelledTombstone() {
        InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
        TravelTaskApplicationService service = service(repository);

        TravelTaskSummary cancelled = service.ensureTaskCancelled(invalidatedEvent("refund-first", "90003", 4L));
        TravelTaskSummary paidLater = service.ensureTask(paymentEvent("payment-late", "90003"));

        assertThat(cancelled.status()).isEqualTo(TravelTaskStatus.CANCELLED);
        assertThat(paidLater).isEqualTo(cancelled);
    }

    @Test
    void givenLowerRefundThenHigherRefund_whenCancelling_thenPromoteCancelledTombstoneVersion() {
        InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
        TravelTaskApplicationService service = service(repository);

        service.ensureTaskCancelled(invalidatedEvent("refund-old", "90004", 5L));
        TravelTaskSummary newer = service.ensureTaskCancelled(invalidatedEvent("refund-new", "90004", 6L));

        assertThat(newer.status()).isEqualTo(TravelTaskStatus.CANCELLED);
        assertThat(newer.orderVersion()).isEqualTo(6L);
    }

    @Test
    void givenInvalidCinemaIds_whenEnsuringPaymentTask_thenRejectEventWithoutCreatingTask() {
        for (String cinemaId : invalidCinemaIds()) {
            InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
            TravelTaskApplicationService service = service(repository);

            assertThatIllegalArgumentException().isThrownBy(() -> service.ensureTask(
                    paymentEvent("invalid-cinema-" + cinemaId, "90005", cinemaId)));

            assertThat(repository.count()).isZero();
        }
    }

    @Test
    void givenInvalidCinemaIds_whenRefundArrivesFirst_thenKeepNullTombstone() {
        for (String cinemaId : invalidCinemaIds()) {
            InMemoryTravelTaskRepository repository = new InMemoryTravelTaskRepository();
            TravelTaskApplicationService service = service(repository);

            service.ensureTaskCancelled(invalidatedEvent("invalid-refund-" + cinemaId, "90006", 4L, cinemaId));

            assertThat(repository.findByOrderId(90006L).orElseThrow().cinemaId()).isNull();
        }
    }

    private TravelTaskApplicationService service(InMemoryTravelTaskRepository repository) {
        AtomicLong ids = new AtomicLong(9_000_000L);
        BusinessIdGenerator idGenerator = ids::incrementAndGet;
        return new TravelTaskApplicationService(repository, idGenerator, FIXED_CLOCK);
    }

    private TravelTaskSummary ensureAfterSignal(
            TravelTaskApplicationService service,
            CountDownLatch start,
            PaymentSucceededEvent event) throws InterruptedException {
        start.await();
        return service.ensureTask(event);
    }

    private PaymentSucceededEvent paymentEvent(String eventId, String orderId) {
        return paymentEvent(eventId, orderId, "60001");
    }

    private PaymentSucceededEvent paymentEvent(String eventId, String orderId, String cinemaId) {
        OffsetDateTime startAt = OffsetDateTime.parse("2026-08-05T19:00:00+08:00");
        return new PaymentSucceededEvent(
                eventId,
                orderId,
                "80001",
                "90001",
                cinemaId,
                "70001",
                "西湖区",
                startAt,
                3L,
                OffsetDateTime.parse("2026-08-04T08:00:00+08:00"));
    }

    private OrderInvalidated invalidatedEvent(String eventId, String orderId, long orderVersion) {
        return invalidatedEvent(eventId, orderId, orderVersion, "60001");
    }

    private OrderInvalidated invalidatedEvent(String eventId, String orderId, long orderVersion, String cinemaId) {
        return new OrderInvalidated(
                eventId, orderId, "80001", cinemaId, "70001", "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"), orderVersion,
                OffsetDateTime.parse("2026-08-04T08:10:00+08:00"), "REFUNDED");
    }

    private List<String> invalidCinemaIds() {
        return List.of("044001", " 44001", "44001 ", "0", "-1", "9223372036854775808");
    }

    /** 用并发 Map 模拟两个唯一索引，让应用层重复恢复逻辑不依赖数据库实现细节。 */
    private static final class InMemoryTravelTaskRepository implements TravelTaskRepository {

        private final ConcurrentHashMap<String, TravelTaskSnapshot> tasksByEventId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, TravelTaskSnapshot> tasksByOrderId = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, String> invalidationEventIds = new ConcurrentHashMap<>();

        @Override
        public Optional<TravelTaskSnapshot> findByPaymentEventId(String paymentEventId) {
            return Optional.ofNullable(tasksByEventId.get(paymentEventId));
        }

        @Override
        public Optional<TravelTaskSnapshot> findByInvalidationEventId(String invalidationEventId) {
            return invalidationEventIds.entrySet().stream()
                    .filter(entry -> invalidationEventId.equals(entry.getValue()))
                    .map(entry -> tasksByOrderId.get(entry.getKey()))
                    .findFirst();
        }

        @Override
        public Optional<TravelTaskSnapshot> findByOrderId(long orderId) {
            return Optional.ofNullable(tasksByOrderId.get(orderId));
        }

        @Override
        public Optional<TravelTaskSnapshot> findById(long id) {
            return tasksByOrderId.values().stream().filter(task -> task.id() == id).findFirst();
        }

        @Override
        public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
            return tasksByOrderId.values().stream()
                    .filter(task -> task.taskId().equals(taskId) && task.userId() == userId)
                    .findFirst();
        }

        @Override
        public boolean updateTriggerAt(
                long id, long expectedVersion, LocalDateTime triggerAt, LocalDateTime updatedAt) {
            return false;
        }

        @Override
        public boolean cancel(long id, long orderVersion, String invalidationEventId, LocalDateTime closedAt) {
            TravelTaskSnapshot task = findById(id).orElse(null);
            if (task == null
                    || task.orderVersion() > orderVersion
                    || task.status() == TravelTaskStatus.COMPLETED
                    || task.status() == TravelTaskStatus.FAILED
                    || (task.status() == TravelTaskStatus.CANCELLED && task.orderVersion() == orderVersion)) {
                return false;
            }
            TravelTaskSnapshot cancelled = new TravelTaskSnapshot(
                    task.id(), task.taskId(), task.userId(), task.orderId(), task.showId(), task.cinemaArea(),
                    task.startAt(), task.triggerAt(), orderVersion, task.version() + 1, TravelTaskStatus.CANCELLED,
                    closedAt, closedAt);
            tasksByOrderId.put(task.orderId(), cancelled);
            tasksByEventId.replaceAll((eventId, existing) -> existing.id() == task.id() ? cancelled : existing);
            invalidationEventIds.put(task.orderId(), invalidationEventId);
            return true;
        }

        @Override
        public void insert(NewTravelTask task) {
            TravelTaskSnapshot snapshot = new TravelTaskSnapshot(
                    task.id(),
                    task.taskId(),
                    task.userId(),
                    task.orderId(),
                    task.showId(),
                    task.cinemaArea(),
                    task.startAt(),
                    task.triggerAt(),
                    task.orderVersion(),
                    0L,
                    TravelTaskStatus.PENDING,
                    null,
                    task.createdAt());
            TravelTaskSnapshot existingByOrder = tasksByOrderId.putIfAbsent(task.orderId(), snapshot);
            if (existingByOrder != null) {
                throw new DuplicateKeyException("orderId 已存在");
            }
            TravelTaskSnapshot existingByEvent = tasksByEventId.putIfAbsent(task.paymentEventId(), snapshot);
            if (existingByEvent != null) {
                tasksByOrderId.remove(task.orderId(), snapshot);
                throw new DuplicateKeyException("eventId 已存在");
            }
        }

        @Override
        public void insertCancelled(NewCancelledTravelTask task) {
            TravelTaskSnapshot snapshot = new TravelTaskSnapshot(
                    task.id(), task.taskId(), task.userId(), task.orderId(), task.showId(), task.cinemaArea(),
                    task.startAt(), task.triggerAt(), task.orderVersion(), 0L, TravelTaskStatus.CANCELLED,
                    task.closedAt(), task.closedAt());
            if (tasksByOrderId.putIfAbsent(task.orderId(), snapshot) != null) {
                throw new DuplicateKeyException("orderId 已存在");
            }
            invalidationEventIds.put(task.orderId(), task.invalidationEventId());
        }

        int count() {
            return tasksByOrderId.size();
        }
    }
}
