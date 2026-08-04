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
}
