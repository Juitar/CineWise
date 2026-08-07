package com.miaoyu.ticket.content.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 排期候选快照只由 D 读写，底层可复用外部数据快照表但不暴露表结构给 A。
 * <p>快照键包含业务日期和影院集合，保持同一查询范围的幂等更新。</p>
 * <p>保存内容只包含标准化 DTO，不保存第三方完整响应。</p>
 * <p>Provider 失败时由 Application 判断快照是否仍在 expiresAt 之前。</p>
 * <p>快照过期不会自动删除最近成功记录，便于审计和故障恢复。</p>
 * <p>返回给 A 前必须标记 degraded 和 fallbackType，避免误当实时数据。</p>
 * <p>端口不暴露 JDBC、SQL、表名或内部主键生成策略。</p>
 * <p>所有时间使用带偏移的 OffsetDateTime，避免不同运行环境产生时区偏差。</p>
 * <p>快照不会承载座位、余座、订单、支付或退票事实。</p>
 */
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
