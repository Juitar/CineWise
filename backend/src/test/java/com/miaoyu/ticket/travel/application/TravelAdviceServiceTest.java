package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TravelAdviceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void givenUnavailableWeather_whenGenerating_thenKeepGeneralAdviceAndReadySnapshot() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryAdviceRepository snapshots = new InMemoryAdviceRepository();
        TravelAdviceSnapshot result = service(tasks, snapshots, unavailableWeather()).generate(1L);

        assertThat(result.weatherJson()).isNull();
        assertThat(result.adviceJson()).contains("天气暂不可用");
        assertThat(result.degraded()).isTrue();
        assertThat(snapshots.findByTaskIdAndVersion(1L, 1L)).contains(result);
    }

    @Test
    void givenSameStaleTaskVersion_whenGeneratingAgain_thenReturnExistingSnapshotWithoutOverwrite() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryAdviceRepository snapshots = new InMemoryAdviceRepository();
        TravelAdviceService service = service(tasks, snapshots, unavailableWeather());
        TravelAdviceSnapshot first = service.generate(1L);
        TravelAdviceSnapshot second = service.generate(1L);

        // 两次调用都使用旧版本投影，第二次应读取第一个赢家的快照而不是覆盖它。
        assertThat(second.taskVersion()).isEqualTo(1L);
        assertThat(first.adviceJson()).isNotBlank();
        assertThat(snapshots.size()).isEqualTo(1);
    }

    private TravelAdviceService service(
            InMemoryTaskRepository tasks, InMemoryAdviceRepository snapshots, WeatherQueryService weather) {
        AtomicLong ids = new AtomicLong(100L);
        BusinessIdGenerator generator = ids::incrementAndGet;
        return new TravelAdviceService(tasks, snapshots, weather, generator, CLOCK);
    }

    private WeatherQueryService unavailableWeather() {
        return new WeatherQueryService((area, now) -> Optional.empty(), (area, now) -> Optional.empty(),
                new EmptyCache(), CLOCK);
    }

    private static final class EmptyCache implements WeatherQueryService.WeatherCache {
        @Override public Optional<WeatherObservation> findValid(String area, OffsetDateTime now) {
            return Optional.empty();
        }
        @Override public WeatherObservation save(WeatherObservation observation) { return observation; }
    }

    private static final class InMemoryTaskRepository implements TravelTaskRepository {
        private TravelTaskSnapshot task = new TravelTaskSnapshot(1L, "1", 9L, 99L, 8L, "西湖区",
                LocalDateTime.of(2026, 8, 5, 19, 0), LocalDateTime.of(2026, 8, 5, 17, 0), 1L,
                0L, TravelTaskStatus.PENDING, null, LocalDateTime.now(CLOCK));
        @Override public Optional<TravelTaskSnapshot> findByPaymentEventId(String eventId) {
            return Optional.empty();
        }
        @Override public Optional<TravelTaskSnapshot> findByOrderId(long orderId) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findById(long id) { return Optional.of(task); }
        @Override public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
            return Optional.of(task);
        }
        @Override public boolean updateTriggerAt(long id, long expectedVersion, LocalDateTime at, LocalDateTime time) {
            return false;
        }
        @Override public void insert(NewTravelTask item) { throw new UnsupportedOperationException(); }
    }

    private static final class InMemoryAdviceRepository implements TravelAdviceRepository {
        private final ConcurrentHashMap<Long, TravelAdviceSnapshot> values = new ConcurrentHashMap<>();
        private long version;
        @Override public synchronized boolean claimVersionForAdvice(
                long taskId, long expected, LocalDateTime time) {
            if (version != expected) { return false; }
            version++;
            return true;
        }
        @Override public void insert(TravelAdviceSnapshot snapshot) {
            values.put(snapshot.taskVersion(), snapshot);
        }
        @Override public Optional<TravelAdviceSnapshot> findByTaskIdAndVersion(long taskId, long taskVersion) {
            return Optional.ofNullable(values.get(taskVersion));
        }
        int size() { return values.size(); }
    }
}
