package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 出行任务的应用层持久化端口。
 *
 * <p>订单模块只能调用 {@link TravelTaskApplicationService}，不能依赖这个端口；将查询和插入留在 D
 * 模块内，才能保证订单唯一约束始终由同一处代码解释。</p>
 */
public interface TravelTaskRepository {

    Optional<TravelTaskSnapshot> findByPaymentEventId(String paymentEventId);

    Optional<TravelTaskSnapshot> findByOrderId(long orderId);

    /** 仅供 D 的调度和建议服务按内部主键读取，外部接口仍使用 taskId 与本人校验。 */
    Optional<TravelTaskSnapshot> findById(long id);

    Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId);

    boolean updateTriggerAt(long id, long expectedVersion, LocalDateTime triggerAt, LocalDateTime updatedAt);

    void insert(NewTravelTask task);

    /** 任务的最小持久化投影，不向 REST 或其他模块暴露数据库字段。 */
    record TravelTaskSnapshot(
            long id,
            String taskId,
            long userId,
            long orderId,
            long showId,
            String cinemaArea,
            LocalDateTime startAt,
            LocalDateTime triggerAt,
            long orderVersion,
            long version,
            TravelTaskStatus status,
            LocalDateTime closedAt,
            LocalDateTime updatedAt) {
    }

    /**
     * 新任务只由支付事件或 A 的补偿调用创建。
     *
     * <p>不把订单实体作为参数，避免 D 在补偿时绕过 A 的公开事件读取订单持久层。</p>
     */
    record NewTravelTask(
            long id,
            String taskId,
            String paymentEventId,
            long userId,
            long orderId,
            long showId,
            String cinemaArea,
            LocalDateTime startAt,
            LocalDateTime triggerAt,
            long orderVersion,
            LocalDateTime createdAt) {
    }
}
