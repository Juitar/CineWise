package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.RefundShowRepository;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 原场次退票判断和同影片替代场次的有界只读SQL。
 *
 * <ul>
 *   <li>不查询movie或cinema内容表，保持A与D模块边界；</li>
 *   <li>ON_SALE和当前时间过滤在数据库侧完成；</li>
 *   <li>原场次ID显式排除，避免退款后推荐同一场次；</li>
 *   <li>日期上界使用半开区间，避免跨日边界重复；</li>
 *   <li>排序固定为start_time、id，保证页面和测试稳定。</li>
 * </ul>
 */
@Mapper
public interface RefundShowMapper {

    /** 退票只需要影片ID和开场时间，不跨模块读取内容表。 */
    @Select("""
            SELECT id AS show_id,
                   movie_id,
                   start_time
              FROM movie_show
             WHERE id = #{showId}
            """)
    RefundShowContextRow findRefundShowContext(@Param("showId") long showId);

    /**
     * 数据库侧固定过滤与排序，空结果保持空集合。
     * startsAfter保护当前时间，日期范围负责限制用户查询窗口。
     */
    @Select("""
            SELECT ms.id AS show_id,
                   ms.cinema_id,
                   ms.start_time,
                   ms.base_price,
                   ms.status,
                   SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_seat_count
              FROM movie_show ms
              LEFT JOIN show_seat ss ON ss.show_id = ms.id
             WHERE ms.movie_id = #{criteria.movieId}
               AND ms.id <> #{criteria.excludedShowId}
               AND ms.status = 'ON_SALE'
               AND ms.start_time > #{criteria.startsAfter}
               AND ms.start_time >= #{criteria.startsAtOrAfter}
               AND ms.start_time < #{criteria.startsBefore}
             GROUP BY ms.id, ms.cinema_id, ms.start_time, ms.base_price, ms.status
             ORDER BY ms.start_time, ms.id
            """)
    List<AlternativeShowRow> findAlternativeShows(
            @Param("criteria") RefundShowRepository.AlternativeShowCriteria criteria);
}
