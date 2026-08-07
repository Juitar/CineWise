package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 电子票自动生命周期所需的显式、最小SQL投影。 */
@Mapper
public interface ElectronicTicketLifecycleMapper {

    /**
     * 只扫描仍为有效票且关联PAID订单的候选；结束时间与票号形成稳定顺序。
     */
    @Select("""
            SELECT tickets.id AS ticket_id,
                   tickets.version AS ticket_version
              FROM electronic_ticket tickets
              INNER JOIN ticket_order orders ON orders.id = tickets.order_id
              INNER JOIN movie_show shows ON shows.id = orders.show_id
             WHERE tickets.status = 'VALID'
               AND orders.status = 'PAID'
               AND shows.end_time <= #{endedAtOrBefore}
             ORDER BY shows.end_time, tickets.id
             LIMIT #{limit}
            """)
    List<ShowEndedTicketCandidateRow> findShowEndedTicketCandidates(
            @Param("endedAtOrBefore") LocalDateTime endedAtOrBefore,
            @Param("limit") int limit);

    /**
     * EXISTS 谓词让H2测试与MySQL运行时共用同一条件更新，同时不依赖跨表物理外键。
     */
    @Update("""
            UPDATE electronic_ticket AS tickets
               SET status = 'INVALIDATED',
                   invalidated_time = #{invalidatedAt},
                   version = version + 1,
                   update_time = #{invalidatedAt}
             WHERE tickets.id = #{ticketId}
               AND tickets.status = 'VALID'
               AND tickets.version = #{expectedVersion}
               AND EXISTS (
                   SELECT 1
                     FROM ticket_order orders
                     INNER JOIN movie_show shows ON shows.id = orders.show_id
                    WHERE orders.id = tickets.order_id
                      AND orders.status = 'PAID'
                      AND shows.end_time <= #{endedAtOrBefore}
               )
            """)
    int invalidateAfterShowEnd(
            @Param("ticketId") long ticketId,
            @Param("expectedVersion") int expectedVersion,
            @Param("endedAtOrBefore") LocalDateTime endedAtOrBefore,
            @Param("invalidatedAt") LocalDateTime invalidatedAt);
}
