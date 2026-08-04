package com.miaoyu.ticket.travel.application;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 建议快照及其任务版本更新的 D 模块持久化端口。
 *
 * <p>条件更新和快照追加必须处在调用方同一个本地事务里；如果快照写入失败，Spring 事务会撤销已经
 * 抢到的任务版本，避免出现没有对应快照的 READY 任务版本。</p>
 */
public interface TravelAdviceRepository {

    boolean claimVersionForAdvice(long taskId, long expectedVersion, LocalDateTime updatedAt);

    void insert(TravelAdviceSnapshot snapshot);

    Optional<TravelAdviceSnapshot> findByTaskIdAndVersion(long taskId, long taskVersion);

    /**
     * 在独立事务中读取已提交快照，供版本抢占失败的一方恢复赢家结果。
     *
     * <p>MySQL 默认可重复读会复用本次生成事务开始时的读取视图；竞争方在条件更新等待后若继续普通读，
     * 可能看不到赢家刚提交的快照。实现必须使用新事务，不能只委托给当前事务的普通查询。</p>
     */
    default Optional<TravelAdviceSnapshot> findCommittedByTaskIdAndVersion(long taskId, long taskVersion) {
        return findByTaskIdAndVersion(taskId, taskVersion);
    }

    default Optional<TravelAdviceSnapshot> findLatestByTaskId(long taskId) {
        return Optional.empty();
    }
}
