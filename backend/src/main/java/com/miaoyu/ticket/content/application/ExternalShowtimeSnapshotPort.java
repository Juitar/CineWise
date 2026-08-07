package com.miaoyu.ticket.content.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 排期候选快照只由 D 读写，底层可复用外部数据快照表但不暴露表结构给 A。 */
public interface ExternalShowtimeSnapshotPort {

    /** 查询键由日期和本地影院集合决定，读取失败时由上层按 expiresAt 判断能否降级。 */
    Optional<Snapshot> find(LocalDate showDate, List<Long> cinemaIds);

    /** 只保存已经标准化的候选，Provider 原始响应和敏感请求参数不会进入快照。 */
    void save(LocalDate showDate, List<Long> cinemaIds, Snapshot snapshot);

    record Snapshot(List<ExternalShowtimeQueryPort.ExternalShowtimeSnapshot> snapshots,
                    OffsetDateTime dataAt, OffsetDateTime expiresAt) {
        public Snapshot {
            // 快照跨越持久化边界后仍必须保持不可变，避免查询线程修改已写入的候选列表。
            snapshots = List.copyOf(snapshots);
        }
    }
}
