package com.miaoyu.ticket.content.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 给 A 读取外部排期候选的唯一公开入口。
 *
 * <p>返回值只描述 D 已映射的第三方排期快照，绝不代表本地余座、可交易价格或下单资格。
 * A 必须在自己的 Application Service 中校验并决定是否导入本地沙箱场次。</p>
 */
public interface ExternalShowtimeQueryPort {

    /**
     * 按业务日期和本地影院 ID 查询候选；空影院集合是正常空结果，不触发外部调用。
     *
     * <p>整体 Provider 不可用且没有未过期快照时实现抛出固定业务码 303004，不能伪装成空排期。</p>
     */
    QueryResult query(Query query);

    /** 调用方只传本地业务 ID，Provider 城市 ID 和外部影院 ID 始终留在 D 的内部边界。 */
    record Query(LocalDate showDate, List<Long> cinemaIds) {
        public Query {
            // 空集合是正常空查询；null 保留给 Application Service 统一映射为 100001，避免构造 DTO 时泄漏 NPE。
            cinemaIds = cinemaIds == null ? null : List.copyOf(cinemaIds);
        }
    }

    /** 批次降级不改变每条候选的来源时间；A 可据此拒绝把陈旧数据导入本地票务事实。 */
    record QueryResult(List<ExternalShowtimeSnapshot> snapshots, boolean degraded, FallbackType fallbackType) {
        public QueryResult {
            snapshots = List.copyOf(snapshots);
        }
    }

    /** 外部排期只有快照降级，没有 Demo 排期回退。 */
    enum FallbackType { SNAPSHOT }

    /** 外部标价只是参考，A 不能直接把它当成本地票价。 */
    enum PriceSemantic { REFERENCE_ONLY }

    /**
     * 已映射且通过字段质量校验的候选。
     *
     * <p>时间均为 Asia/Shanghai 带偏移时间；NetStart 没有可靠散场时间时 endTime 为空，A 不得自行推算。</p>
     */
    record ExternalShowtimeSnapshot(String provider, String externalShowId, String externalMovieId,
                                   String externalCinemaId, Long movieId, Long cinemaId,
                                   OffsetDateTime startTime, OffsetDateTime endTime, BigDecimal listedPrice,
                                   PriceSemantic priceSemantic, OffsetDateTime dataAt, OffsetDateTime expiresAt,
                                   boolean expired) { }
}
