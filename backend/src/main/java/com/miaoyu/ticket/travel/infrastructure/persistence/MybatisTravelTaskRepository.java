package com.miaoyu.ticket.travel.infrastructure.persistence;

import com.miaoyu.ticket.travel.application.TravelTaskRepository;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 将 travel_task 的 SQL 行对象映射为 D 的应用层端口类型。
 *
 * <p>该适配器不包含创建规则和重复恢复逻辑，避免 Mapper 变成跨事务的业务入口。</p>
 */
@Repository
public class MybatisTravelTaskRepository implements TravelTaskRepository {

    private final TravelTaskPersistenceMapper mapper;

    public MybatisTravelTaskRepository(TravelTaskPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TravelTaskSnapshot> findByPaymentEventId(String paymentEventId) {
        return Optional.ofNullable(mapper.findByPaymentEventId(paymentEventId)).map(this::toSnapshot);
    }

    @Override
    public Optional<TravelTaskSnapshot> findByInvalidationEventId(String invalidationEventId) {
        return Optional.ofNullable(mapper.findByInvalidationEventId(invalidationEventId)).map(this::toSnapshot);
    }

    @Override
    public Optional<TravelTaskSnapshot> findByOrderId(long orderId) {
        return Optional.ofNullable(mapper.findByOrderId(orderId)).map(this::toSnapshot);
    }

    @Override
    public Optional<TravelTaskSnapshot> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toSnapshot);
    }

    @Override
    public Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId) {
        return Optional.ofNullable(mapper.findByTaskIdAndUserId(taskId, userId)).map(this::toSnapshot);
    }

    @Override
    public boolean updateTriggerAt(long id, long expectedVersion, java.time.LocalDateTime triggerAt,
                                   java.time.LocalDateTime updatedAt) {
        return mapper.updateTriggerAt(id, expectedVersion, triggerAt, updatedAt) == 1;
    }

    @Override
    public List<TravelTaskSnapshot> listDueForAdvice(java.time.LocalDateTime now, int limit) {
        return mapper.listDueForAdvice(now, limit).stream().map(this::toSnapshot).toList();
    }

    @Override
    public boolean completeIfElapsed(long id, java.time.LocalDateTime elapsedBefore, java.time.LocalDateTime closedAt) {
        return mapper.completeIfElapsed(id, elapsedBefore, closedAt) == 1;
    }

    @Override
    public List<Long> listElapsedTaskIds(java.time.LocalDateTime completedAt, int limit) {
        return mapper.listElapsedTaskIds(completedAt, limit);
    }

    @Override
    public boolean cancel(long id, long invalidatedOrderVersion, String invalidationEventId,
                          java.time.LocalDateTime closedAt) {
        return mapper.cancel(id, invalidatedOrderVersion, invalidationEventId, closedAt) == 1;
    }

    @Override
    public void insert(NewTravelTask task) {
        int inserted = mapper.insert(new TravelTaskInsertRow(
                task.id(),
                task.taskId(),
                task.paymentEventId(),
                task.userId(),
                task.orderId(),
                task.showId(),
                task.cinemaArea(),
                task.startAt(),
                task.triggerAt(),
                task.orderVersion(),
                task.createdAt()));
        if (inserted != 1) {
            throw new IllegalStateException("出行任务写入行数异常");
        }
    }

    @Override
    public void insertCancelled(NewCancelledTravelTask task) {
        int inserted = mapper.insertCancelled(new TravelTaskCancelledInsertRow(
                task.id(), task.taskId(), task.invalidationEventId(), task.userId(), task.orderId(), task.showId(),
                task.cinemaArea(), task.startAt(), task.triggerAt(), task.orderVersion(), task.closedAt()));
        if (inserted != 1) {
            throw new IllegalStateException("出行取消墓碑写入行数异常");
        }
    }

    private TravelTaskSnapshot toSnapshot(TravelTaskRow row) {
        return new TravelTaskSnapshot(
                row.id(),
                row.taskId(),
                row.userId(),
                row.orderId(),
                row.showId(),
                row.cinemaArea(),
                row.startAt(),
                row.triggerAt(),
                row.orderVersion(),
                row.version(),
                TravelTaskStatus.valueOf(row.status()),
                row.closedAt(),
                row.updatedAt());
    }
}
