package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** 票务只读仓储；返回数据前必须完成状态与时间窗口过滤。 */
public interface ShowQueryRepository {

    /** 查询满足全部售卖与时间条件的有界场次列表。 */
    List<ShowSnapshot> findSaleableShows(QueryCriteria criteria);

    /** 按多个影院查询已排除售罄记录的有界可售场次。 */
    List<ShowSnapshot> findSaleableShowsByCinemaIds(BatchQueryCriteria criteria);

    /** 查询座位图头部及场次售卖状态，不存在时返回空。 */
    Optional<ShowSeatHeader> findShowSeatHeader(long showId);

    /** 支付成功事件只读取影院和开场时间，不携带库存或内容模块字段。 */
    Optional<ShowContext> findShowContext(long showId);

    /** 按稳定行号、座号顺序查询场次的全部座位。 */
    List<SeatSnapshot> findSeats(long showId);

    record QueryCriteria(
            long movieId,
            long cinemaId,
            LocalDateTime startsAfter,
            LocalDateTime startsBefore,
            LocalDateTime dateStart,
            LocalDateTime dateEnd,
            LocalTime timeFrom,
            LocalTime timeTo) {
    }

    record BatchQueryCriteria(
            List<Long> cinemaIds,
            LocalDateTime startsAfter,
            LocalDateTime dateStart,
            LocalDateTime dateEnd,
            int fetchLimit) {
    }

    record ShowSnapshot(
            long showId,
            long movieId,
            long cinemaId,
            long auditoriumId,
            String auditoriumName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String languageVersion,
            BigDecimal basePrice,
            int availableSeatCount,
            String status,
            String dataType,
            String source,
            int version,
            LocalDateTime updatedAt) {
    }

    record ShowSeatHeader(
            long showId,
            long auditoriumId,
            String auditoriumName,
            int rowCount,
            int seatCount,
            int availableSeatCount,
            String status,
            LocalDateTime startTime,
            int version,
            LocalDateTime updatedAt) {
    }

    /**
     * 跨订单用例复用的最小权威场次上下文。
     * 影院名称和区域不在该投影中，避免A把D的内容事实复制成第二份权威数据。
     */
    record ShowContext(long showId, long cinemaId, LocalDateTime startTime) {
    }

    record SeatSnapshot(
            long seatId,
            String rowNo,
            String seatNo,
            String seatLabel,
            String status,
            int version) {
    }
}
