package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.AvailableDateQueryRepository;
import com.miaoyu.ticket.ticketing.application.ShowQueryRepository;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 场次和座位快照的显式只读 SQL，查询范围与排序均在数据库侧固定。 */
@Mapper
public interface TicketingQueryMapper {

    /**
     * 日期数量只按场次事实聚合，不连接座位表；售罄展示语义继续由具体场次查询负责。
     * CAST写法同时兼容项目的MySQL 8生产基线与H2 MySQL模式契约测试。
     */
    @Select("""
            SELECT CAST(ms.start_time AS DATE) AS show_date,
                   COUNT(*) AS show_count
              FROM movie_show ms
             WHERE ms.movie_id = #{criteria.movieId}
               AND ms.cinema_id = #{criteria.cinemaId}
               AND ms.status = 'ON_SALE'
               AND ms.start_time > #{criteria.startsAfter}
               AND ms.start_time < #{criteria.startsBefore}
             GROUP BY CAST(ms.start_time AS DATE)
             ORDER BY show_date
            """)
    List<AvailableDateQueryRow> findAvailableDates(
            @Param("criteria") AvailableDateQueryRepository.QueryCriteria criteria);

    /** 查询滚动窗口内的可售场次，并在数据库侧汇总实时可用座位数。 */
    @Select("""
            <script>
            SELECT ms.id AS show_id,
                   ms.movie_id,
                   ms.cinema_id,
                   ms.auditorium_id,
                   a.name AS auditorium_name,
                   ms.start_time,
                   ms.end_time,
                   ms.language_version,
                   ms.base_price,
                   SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_seat_count,
                   ms.status,
                   ms.data_type,
                   ms.version,
                   ms.update_time AS updated_at
              FROM movie_show ms
              JOIN auditorium a ON a.id = ms.auditorium_id
              LEFT JOIN show_seat ss ON ss.show_id = ms.id
             WHERE ms.movie_id = #{criteria.movieId}
               AND ms.cinema_id = #{criteria.cinemaId}
               AND ms.status = 'ON_SALE'
               AND ms.start_time &gt; #{criteria.startsAfter}
               AND ms.start_time &lt; #{criteria.startsBefore}
            <if test="criteria.dateStart != null">
               AND ms.start_time &gt;= #{criteria.dateStart}
               AND ms.start_time &lt; #{criteria.dateEnd}
            </if>
            <if test="criteria.timeFrom != null">
               AND CAST(ms.start_time AS TIME) &gt;= #{criteria.timeFrom}
            </if>
            <if test="criteria.timeTo != null">
               AND CAST(ms.start_time AS TIME) &lt; #{criteria.timeTo}
            </if>
             GROUP BY ms.id, ms.movie_id, ms.cinema_id, ms.auditorium_id, a.name,
                      ms.start_time, ms.end_time, ms.language_version, ms.base_price,
                      ms.status, ms.data_type, ms.version, ms.update_time
             ORDER BY ms.start_time, ms.id
            </script>
            """)
    List<ShowQueryRow> findSaleableShows(@Param("criteria") ShowQueryRepository.QueryCriteria criteria);

    /** 推荐批量查询在数据库侧排除售罄场次，并只多读取一条用于截断探测。 */
    @Select("""
            <script>
            SELECT ms.id AS show_id,
                   ms.movie_id,
                   ms.cinema_id,
                   ms.auditorium_id,
                   a.name AS auditorium_name,
                   ms.start_time,
                   ms.end_time,
                   ms.language_version,
                   ms.base_price,
                   SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_seat_count,
                   ms.status,
                   ms.data_type,
                   ms.version,
                   ms.update_time AS updated_at
              FROM movie_show ms
              JOIN auditorium a ON a.id = ms.auditorium_id
              LEFT JOIN show_seat ss ON ss.show_id = ms.id
             WHERE ms.cinema_id IN
            <foreach collection="criteria.cinemaIds" item="cinemaId" open="(" separator="," close=")">
                #{cinemaId}
            </foreach>
               AND ms.status = 'ON_SALE'
               AND ms.start_time &gt; #{criteria.startsAfter}
               AND ms.start_time &gt;= #{criteria.dateStart}
               AND ms.start_time &lt; #{criteria.dateEnd}
            <if test="criteria.timeFrom != null">
               AND CAST(ms.start_time AS TIME) &gt;= #{criteria.timeFrom}
               AND CAST(ms.start_time AS TIME) &lt; #{criteria.timeTo}
            </if>
             GROUP BY ms.id, ms.movie_id, ms.cinema_id, ms.auditorium_id, a.name,
                      ms.start_time, ms.end_time, ms.language_version, ms.base_price,
                      ms.status, ms.data_type, ms.version, ms.update_time
            HAVING SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) &gt; 0
             ORDER BY ms.start_time, ms.id
             LIMIT #{criteria.fetchLimit}
            </script>
            """)
    List<ShowQueryRow> findSaleableShowsByCinemaIds(
            @Param("criteria") ShowQueryRepository.BatchQueryCriteria criteria);

    /** 查询座位图头部，同时取场次与座位中较新的更新时间作为快照时间。 */
    @Select("""
            SELECT ms.id AS show_id,
                   ms.auditorium_id,
                   a.name AS auditorium_name,
                   a.row_count,
                   a.seat_count,
                   SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_seat_count,
                   ms.status,
                   ms.start_time,
                   ms.version,
                   GREATEST(ms.update_time, COALESCE(MAX(ss.update_time), ms.update_time)) AS updated_at
              FROM movie_show ms
              JOIN auditorium a ON a.id = ms.auditorium_id
              LEFT JOIN show_seat ss ON ss.show_id = ms.id
             WHERE ms.id = #{showId}
             GROUP BY ms.id, ms.auditorium_id, a.name, a.row_count, a.seat_count,
                      ms.status, ms.start_time, ms.version, ms.update_time
            """)
    ShowSeatHeaderRow findShowSeatHeader(@Param("showId") long showId);

    /** 支付事件仅查询A拥有的场次事实，影院区域仍由D的公开端口提供。 */
    @Select("""
            SELECT id AS show_id,
                   cinema_id,
                   start_time
              FROM movie_show
             WHERE id = #{showId}
            """)
    ShowContextRow findShowContext(@Param("showId") long showId);

    /** 按行号、座号和主键稳定排序，返回场次的完整座位集合。 */
    @Select("""
            SELECT id AS seat_id,
                   row_no,
                   seat_no,
                   seat_label,
                   status,
                   version
              FROM show_seat
             WHERE show_id = #{showId}
             ORDER BY row_no, seat_no, id
            """)
    List<SeatQueryRow> findSeats(@Param("showId") long showId);
}
