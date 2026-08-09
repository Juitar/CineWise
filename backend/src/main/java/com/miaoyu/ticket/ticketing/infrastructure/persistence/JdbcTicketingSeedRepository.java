package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.TicketingSeedRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 票务种子的 JDBC 适配器，只插入缺失数据，绝不覆盖已有座位交易状态。 */
@Repository
public class JdbcTicketingSeedRepository implements TicketingSeedRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTicketingSeedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long ensureAuditorium(AuditoriumSeed row) {
        Long existingId = findAuditoriumId(row.cinemaId(), row.name());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO auditorium (
                        id, cinema_id, name, hall_type, row_count, seat_count,
                        data_type, status, version, create_time, update_time
                    ) VALUES (?, ?, ?, 'NORMAL', ?, ?, 'MOCK', 'ENABLED', 0, ?, ?)
                    """,
                    row.id(),
                    row.cinemaId(),
                    row.name(),
                    row.rowCount(),
                    row.seatCount(),
                    Timestamp.valueOf(row.createdAt()),
                    Timestamp.valueOf(row.createdAt()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            // 并发初始化由数据库唯一约束裁决，失败方重查已存在的权威影厅主键。
            Long auditoriumId = findAuditoriumId(row.cinemaId(), row.name());
            if (auditoriumId == null) {
                throw duplicate;
            }
            return auditoriumId;
        }
    }

    @Override
    public long ensureShow(ShowSeed row) {
        Long existingId = findShowId(row.auditoriumId(), row.startTime());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO movie_show (
                        id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                        language_version, base_price, data_type, source, status, version,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MOCK', 'demo-seed', 'ON_SALE', 0, ?, ?)
                    """,
                    row.id(),
                    row.movieId(),
                    row.cinemaId(),
                    row.auditoriumId(),
                    Timestamp.valueOf(row.startTime()),
                    Timestamp.valueOf(row.endTime()),
                    row.languageVersion(),
                    row.basePrice(),
                    Timestamp.valueOf(row.createdAt()),
                    Timestamp.valueOf(row.createdAt()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            // 同一影厅同一开场时间只能有一个场次，重复执行或并发执行均复用已有记录。
            Long showId = findShowId(row.auditoriumId(), row.startTime());
            if (showId == null) {
                throw duplicate;
            }
            return showId;
        }
    }

    @Override
    public boolean hasExternalShowForDate(long cinemaId, LocalDate showDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM movie_show
                 WHERE cinema_id = ?
                   AND source = 'external-sandbox'
                   AND start_time >= ?
                   AND start_time < ?
                """, Integer.class,
                cinemaId,
                Timestamp.valueOf(showDate.atStartOfDay()),
                Timestamp.valueOf(showDate.plusDays(1).atStartOfDay()));
        return count != null && count > 0;
    }

    @Override
    public Set<String> findSeatKeys(long showId) {
        List<String> keys = jdbcTemplate.query("""
                SELECT row_no, seat_no
                  FROM show_seat
                 WHERE show_id = ?
                """, (resultSet, rowNumber) -> resultSet.getString("row_no")
                        + ':' + resultSet.getString("seat_no"), showId);
        return new HashSet<>(keys);
    }

    @Override
    public void insertSeats(List<SeatSeed> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO show_seat (
                    id, show_id, row_no, seat_no, seat_label, status,
                    lock_order_no, lock_expire_time, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, 'AVAILABLE', NULL, NULL, 0, ?, ?)
                """, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        SeatSeed row = rows.get(index);
                        statement.setLong(1, row.id());
                        statement.setLong(2, row.showId());
                        statement.setString(3, row.rowNo());
                        statement.setString(4, row.seatNo());
                        statement.setString(5, row.seatLabel());
                        statement.setTimestamp(6, Timestamp.valueOf(row.createdAt()));
                        statement.setTimestamp(7, Timestamp.valueOf(row.createdAt()));
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
    }

    @Override
    public int deleteExpiredUnreferencedDemoShows(LocalDateTime endedBefore, int limit) {
        List<Long> showIds = jdbcTemplate.query("""
                SELECT ms.id
                  FROM movie_show ms
                 WHERE ms.source = 'demo-seed'
                   AND ms.end_time < ?
                 ORDER BY ms.end_time ASC, ms.id ASC
                 LIMIT ?
                """, (resultSet, rowNumber) -> resultSet.getLong("id"),
                Timestamp.valueOf(endedBefore), limit);
        int deletedCount = 0;
        for (Long showId : showIds) {
            if (deleteExpiredUnreferencedDemoShow(showId, endedBefore)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    /**
     * 清理候选仅用于限制批量大小，不能作为删除依据。这里按场次、座位、订单依次加锁并重新检查，
     * 使锁座/建单要么先提交并令本次跳过，要么等待本次删除结束后按正常场次校验失败。
     */
    private boolean deleteExpiredUnreferencedDemoShow(long showId, LocalDateTime endedBefore) {
        List<Long> lockedShowIds = jdbcTemplate.query("""
                SELECT id
                  FROM movie_show
                 WHERE id = ?
                   AND source = 'demo-seed'
                   AND end_time < ?
                 FOR UPDATE
                """, (resultSet, rowNumber) -> resultSet.getLong("id"),
                showId, Timestamp.valueOf(endedBefore));
        if (lockedShowIds.isEmpty()) {
            return false;
        }

        List<SeatState> seats = jdbcTemplate.query("""
                SELECT status, lock_order_no, lock_expire_time
                  FROM show_seat
                 WHERE show_id = ?
                 FOR UPDATE
                """, (resultSet, rowNumber) -> new SeatState(
                resultSet.getString("status"),
                resultSet.getString("lock_order_no"),
                resultSet.getTimestamp("lock_expire_time")), showId);
        if (seats.stream().anyMatch(SeatState::isReferencedOrLocked)) {
            return false;
        }

        List<Long> orderIds = jdbcTemplate.query("""
                SELECT id
                  FROM ticket_order
                 WHERE show_id = ?
                 FOR UPDATE
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), showId);
        if (!orderIds.isEmpty()) {
            return false;
        }

        // 只有锁定后仍无交易引用的可再生座位图才会被删除。
        jdbcTemplate.update("DELETE FROM show_seat WHERE show_id = ?", showId);
        return jdbcTemplate.update("""
                DELETE FROM movie_show
                 WHERE id = ?
                   AND source = 'demo-seed'
                   AND end_time < ?
                   AND NOT EXISTS (
                       SELECT 1 FROM ticket_order orders WHERE orders.show_id = movie_show.id
                   )
                """, showId, Timestamp.valueOf(endedBefore)) == 1;
    }

    private record SeatState(String status, String lockOrderNo, Timestamp lockExpireTime) {

        private boolean isReferencedOrLocked() {
            return !"AVAILABLE".equals(status) || lockOrderNo != null || lockExpireTime != null;
        }
    }

    private Long findAuditoriumId(long cinemaId, String name) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                  FROM auditorium
                 WHERE cinema_id = ?
                   AND name = ?
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), cinemaId, name);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Long findShowId(long auditoriumId, java.time.LocalDateTime startTime) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                  FROM movie_show
                 WHERE auditorium_id = ?
                   AND start_time = ?
                """,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                auditoriumId,
                Timestamp.valueOf(startTime));
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
