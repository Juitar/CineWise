package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 出行任务的应用层持久化端口。
 *
 * <p>订单模块只能调用 {@link TravelTaskApplicationService}，不能依赖这个端口；将查询和插入留在 D
 * 模块内，才能保证订单唯一约束始终由同一处代码解释。</p>
 */
public interface TravelTaskRepository {

    Optional<TravelTaskSnapshot> findByPaymentEventId(String paymentEventId);

    /** 默认实现供只验证查询能力的测试桩保持兼容；生产适配器必须按退款事件键查询。 */
    default Optional<TravelTaskSnapshot> findByInvalidationEventId(String invalidationEventId) {
        return Optional.empty();
    }

    Optional<TravelTaskSnapshot> findByOrderId(long orderId);

    /** 仅供 D 的调度和建议服务按内部主键读取，外部接口仍使用 taskId 与本人校验。 */
    Optional<TravelTaskSnapshot> findById(long id);

    Optional<TravelTaskSnapshot> findByTaskIdAndUserId(String taskId, long userId);

    boolean updateTriggerAt(long id, long expectedVersion, LocalDateTime triggerAt, LocalDateTime updatedAt);

    /** 调度只领取已到提醒时间的 PENDING 任务，单次上限由 D 配置控制。 */
    default List<TravelTaskSnapshot> listDueForAdvice(LocalDateTime now, int limit) {
        return List.of();
    }

    /** 观影结束两小时后关闭已完成建议的任务；退款终态和失败终态绝不被覆盖。 */
    default boolean completeIfElapsed(long id, LocalDateTime completedAt, LocalDateTime updatedAt) {
        return false;
    }

    /** 只返回尚未关闭、且已超过观影结束窗口的内部任务号，避免调度层扫描订单或用户数据。 */
    default List<Long> listElapsedTaskIds(LocalDateTime completedAt, int limit) {
        return List.of();
    }

    /**
     * 仅将尚未结束、且订单版本不高于退款事件的任务置为取消。
     *
     * <p>条件更新同时阻止迟到的旧退款覆盖新版本，并让建议生成中的竞争方在提交时看到状态已经变化。</p>
     */
    default boolean cancel(
            long id,
            long invalidatedOrderVersion,
            String invalidationEventId,
            LocalDateTime closedAt) {
        return false;
    }

    void insert(NewTravelTask task);

    /** 退款先于支付事件到达时创建的终态墓碑，防止随后补偿支付重新开启提醒。 */
    default void insertCancelled(NewCancelledTravelTask task) {
        throw new UnsupportedOperationException("当前任务仓储不支持退款取消墓碑");
    }

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

    /** 取消墓碑仍保留 A 事件中的最小订单事实，不读取订单模块私有数据。 */
    record NewCancelledTravelTask(
            long id,
            String taskId,
            String invalidationEventId,
            long userId,
            long orderId,
            long showId,
            String cinemaArea,
            LocalDateTime startAt,
            LocalDateTime triggerAt,
            long orderVersion,
            LocalDateTime closedAt) {
    }
}
