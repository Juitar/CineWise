package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.ExternalShowtimeSandboxImportRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** V019 映射和本地沙箱票务事实的 JDBC 适配器。 */
@Repository
public class JdbcExternalShowtimeSandboxImportRepository implements ExternalShowtimeSandboxImportRepository {

    private final JdbcTemplate jdbc;

    public JdbcExternalShowtimeSandboxImportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> findMappedShowId(String provider, String cinemaId, String showId) {
        List<Long> ids = jdbc.query("""
                SELECT show_id FROM ticketing_external_showtime_mapping
                 WHERE provider = ? AND external_cinema_id = ? AND external_show_id = ?
                """, (rs, ignored) -> rs.getLong(1), provider, cinemaId, showId);
        return ids.stream().findFirst();
    }

    @Override
    public Optional<Long> findMappedShowIdAfterConflict(String provider, String cinemaId, String showId) {
        List<Long> ids = jdbc.query("""
                SELECT show_id FROM ticketing_external_showtime_mapping
                 WHERE provider = ? AND external_cinema_id = ? AND external_show_id = ? FOR UPDATE
                """, (rs, ignored) -> rs.getLong(1), provider, cinemaId, showId);
        return ids.stream().findFirst();
    }

    @Override
    public long ensureSandboxAuditorium(AuditoriumRow row) {
        List<Long> ids = jdbc.query("SELECT id FROM auditorium WHERE cinema_id = ? AND name = ?",
                (rs, ignored) -> rs.getLong(1), row.cinemaId(), row.name());
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        try {
            jdbc.update("""
                    INSERT INTO auditorium (id, cinema_id, name, hall_type, row_count, seat_count,
                    data_type, status, version, create_time, update_time)
                    VALUES (?, ?, ?, 'NORMAL', ?, ?, 'SANDBOX', 'ENABLED', 0, ?, ?)
                    """, row.id(), row.cinemaId(), row.name(), row.rowCount(), row.seatCount(),
                    Timestamp.valueOf(row.now()), Timestamp.valueOf(row.now()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject("SELECT id FROM auditorium WHERE cinema_id = ? AND name = ?",
                    Long.class, row.cinemaId(), row.name());
        }
    }

    @Override
    public void insertSandboxShow(ShowRow row) {
        jdbc.update("""
                INSERT INTO movie_show (id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                language_version, base_price, data_type, source, status, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SANDBOX', 'external-sandbox', 'ON_SALE', 0, ?, ?)
                """, row.id(), row.movieId(), row.cinemaId(), row.auditoriumId(),
                Timestamp.valueOf(row.startTime()), Timestamp.valueOf(row.endTime()), row.languageVersion(),
                row.basePrice(), Timestamp.valueOf(row.now()), Timestamp.valueOf(row.now()));
    }

    @Override
    public void insertSeats(List<SeatRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO show_seat (id, show_id, row_no, seat_no, seat_label, status, lock_order_no,
                lock_expire_time, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'AVAILABLE', NULL, NULL, 0, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                SeatRow row = rows.get(index);
                statement.setLong(1, row.id());
                statement.setLong(2, row.showId());
                statement.setString(3, row.rowNo());
                statement.setString(4, row.seatNo());
                statement.setString(5, row.seatLabel());
                statement.setTimestamp(6, Timestamp.valueOf(row.now()));
                statement.setTimestamp(7, Timestamp.valueOf(row.now()));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Override
    public void insertMapping(MappingRow row) {
        jdbc.update("""
                INSERT INTO ticketing_external_showtime_mapping (id, provider, external_cinema_id,
                external_show_id, show_id, import_mode, source, data_at, expires_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'SANDBOX_REFERENCE', ?, ?, ?, ?, ?)
                """, row.id(), row.provider(), row.externalCinemaId(), row.externalShowId(), row.showId(),
                row.source(), Timestamp.valueOf(row.dataAt()), Timestamp.valueOf(row.expiresAt()),
                Timestamp.valueOf(row.now()), Timestamp.valueOf(row.now()));
    }
}
