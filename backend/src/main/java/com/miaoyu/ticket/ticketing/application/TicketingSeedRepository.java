package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 票务模块固定影厅、场次与座位数据的持久化端口。 */
public interface TicketingSeedRepository {

    /** 按影院与影厅名称幂等创建影厅，并返回权威主键。 */
    long ensureAuditorium(AuditoriumSeed row);

    /** 按影厅与开场时间幂等创建场次，并返回权威主键。 */
    long ensureShow(ShowSeed row);

    /**
     * 判断指定影院和业务日期是否已有真实沙箱场次。
     *
     * <p>真实沙箱是同日期票务展示的优先来源；种子生成只据此跳过新增 Mock，绝不删除已有记录。</p>
     */
    boolean hasExternalShowForDate(long cinemaId, LocalDate showDate);

    /** 查询场次已有的行号与座号业务键，用于只补缺失座位。 */
    Set<String> findSeatKeys(long showId);

    /** 批量插入缺失座位；调用方不得传入已有座位以覆盖交易状态。 */
    void insertSeats(List<SeatSeed> rows);

    /** 删除指定时间前已结束、未产生交易且没有锁座的演示场次，返回实际删除的场次数。 */
    int deleteExpiredUnreferencedDemoShows(LocalDateTime endedBefore, int limit);

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
