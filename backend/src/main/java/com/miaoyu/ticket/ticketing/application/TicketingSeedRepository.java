package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 票务模块固定影厅、场次与座位数据的持久化端口。 */
public interface TicketingSeedRepository {

    /** 按影院与影厅名称幂等创建影厅，并返回权威主键。 */
    long ensureAuditorium(AuditoriumSeed row);

    /** 按影厅与开场时间幂等创建场次，并返回权威主键。 */
    long ensureShow(ShowSeed row);

    /** 查询场次已有的行号与座号业务键，用于只补缺失座位。 */
    Set<String> findSeatKeys(long showId);

    /** 批量插入缺失座位；调用方不得传入已有座位以覆盖交易状态。 */
    void insertSeats(List<SeatSeed> rows);

    record AuditoriumSeed(
            long id,
            long cinemaId,
            String name,
            int rowCount,
            int seatCount,
            LocalDateTime createdAt) {
    }

    record ShowSeed(
            long id,
            long movieId,
            long cinemaId,
            long auditoriumId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String languageVersion,
            BigDecimal basePrice,
            LocalDateTime createdAt) {
    }

    record SeatSeed(
            long id,
            long showId,
            String rowNo,
            String seatNo,
            String seatLabel,
            LocalDateTime createdAt) {

        public String businessKey() {
            return rowNo + ':' + seatNo;
        }
    }
}
