package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.SeatLockRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 座位库存的显式查询和条件更新SQL。 */
@Mapper
public interface TicketingSeatLockMapper {

    @Select("""
            SELECT id AS show_id,
                   base_price,
                   status,
                   start_time
              FROM movie_show
             WHERE id = #{showId}
            """)
    ShowForLockRow findShow(@Param("showId") long showId);

    /** `seatIds`已由应用服务去重、校验且限制为最多6个。 */
    @Select("""
            <script>
            SELECT id AS seat_id,
                   row_no,
                   seat_no,
                   status,
                   version
              FROM show_seat
             WHERE show_id = #{showId}
               AND id IN
               <foreach collection="seatIds" item="seatId" open="(" separator="," close=")">
                   #{seatId}
               </foreach>
             ORDER BY id
            </script>
            """)
    List<SeatForLockRow> findSeats(
            @Param("showId") long showId,
            @Param("seatIds") List<Long> seatIds);

    /** 状态、场次和版本一起构成防超卖的数据库最终守门。 */
    @Update("""
            UPDATE show_seat
               SET status = 'LOCKED',
                   lock_order_no = #{update.orderNo},
                   lock_expire_time = #{update.lockExpiresAt},
                   version = version + 1,
                   update_time = #{update.updatedAt}
             WHERE id = #{update.seatId}
               AND show_id = #{update.showId}
               AND status = 'AVAILABLE'
               AND version = #{update.expectedVersion}
            """)
    int lockSeat(@Param("update") SeatLockRepository.SeatLockUpdate update);

    @Update("""
            UPDATE show_seat
               SET status = 'AVAILABLE',
                   lock_order_no = NULL,
                   lock_expire_time = NULL,
                   version = version + 1,
                   update_time = #{updatedAt}
             WHERE status = 'LOCKED'
               AND lock_order_no = #{orderNo}
            """)
    int releaseLockedSeats(
            @Param("orderNo") String orderNo,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE show_seat
               SET status = 'SOLD',
                   lock_order_no = NULL,
                   lock_expire_time = NULL,
                   version = version + 1,
                   update_time = #{soldAt}
             WHERE status = 'LOCKED'
               AND lock_order_no = #{orderNo}
            """)
    int sellLockedSeats(
            @Param("orderNo") String orderNo,
            @Param("soldAt") LocalDateTime soldAt);

    /** 影响查询只统计订单快照引用且仍为SOLD的座位。 */
    @Select("""
            SELECT COUNT(*)
              FROM show_seat ss
             WHERE ss.status = 'SOLD'
               AND ss.id IN (
                   SELECT tos.show_seat_id
                     FROM ticket_order_seat tos
                    WHERE tos.order_id = #{orderId}
               )
            """)
    int countSoldSeats(@Param("orderId") long orderId);

    /**
     * 订单座位快照限定资源范围，SOLD条件保证重复调用不会释放其他状态。
     * 影响行数由退款事务与订单票数比较，部分更新会触发整笔事务回滚。
     */
    @Update("""
            UPDATE show_seat
               SET status = 'AVAILABLE',
                   lock_order_no = NULL,
                   lock_expire_time = NULL,
                   version = version + 1,
                   update_time = #{refundedAt}
             WHERE status = 'SOLD'
               AND id IN (
                   SELECT tos.show_seat_id
                     FROM ticket_order_seat tos
                    WHERE tos.order_id = #{orderId}
               )
            """)
    int releaseSoldSeats(
            @Param("orderId") long orderId,
            @Param("refundedAt") LocalDateTime refundedAt);
}
