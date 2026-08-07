package com.miaoyu.ticket.content.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 排期候选快照只由 D 读写，底层可复用外部数据快照表但不暴露表结构给 A。 */
public interface ExternalShowtimeSnapshotPort {

    Optional<Snapshot> find(LocalDate showDate, List<Long> cinemaIds);

    void save(LocalDate showDate, List<Long> cinemaIds, Snapshot snapshot);

    record Snapshot(List<ExternalShowtimeQueryPort.ExternalShowtimeSnapshot> snapshots,
                    OffsetDateTime dataAt, OffsetDateTime expiresAt) {
        public Snapshot {
            snapshots = List.copyOf(snapshots);
        }
    }
}
