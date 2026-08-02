package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSeedRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 内容种子的 JDBC 适配器，确保影片、影院写入始终封装在内容模块内。 */
@Repository
public class JdbcContentSeedRepository implements ContentSeedRepository {

    private static final String SEED_SOURCE = "demo-seed";
    private static final String SOURCE_TYPE = "MOCK";

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentSeedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long ensureMovie(MovieSeed row) {
        Long existingId = findMovieId(row.sourceMovieId());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO movie (
                        id, source_movie_id, title, genres_json, duration_minutes, rating,
                        source_type, source, data_time, expires_at, version, deleted_at,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """,
                    row.id(),
                    row.sourceMovieId(),
                    row.title(),
                    row.genresJson(),
                    row.durationMinutes(),
                    row.rating(),
                    SOURCE_TYPE,
                    SEED_SOURCE,
                    Timestamp.valueOf(row.dataTime()),
                    nullableTimestamp(row.expiresAt()),
                    Timestamp.valueOf(row.dataTime()),
                    Timestamp.valueOf(row.dataTime()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            // 并发初始化时另一事务可能先写入同一业务键，此处重查权威主键以保持幂等。
            return requireMovieId(row.sourceMovieId(), duplicate);
        }
    }

    @Override
    public long ensureCinema(CinemaSeed row) {
        Long existingId = findCinemaId(row.sourceCinemaId());
        if (existingId != null) {
            return existingId;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO cinema (
                        id, source_cinema_id, name, city_code, area, address, longitude, latitude,
                        source_type, source, data_time, expires_at, version, deleted_at,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, ?)
                    """,
                    row.id(),
                    row.sourceCinemaId(),
                    row.name(),
                    row.cityCode(),
                    row.area(),
                    row.address(),
                    row.longitude(),
                    row.latitude(),
                    SOURCE_TYPE,
                    SEED_SOURCE,
                    Timestamp.valueOf(row.dataTime()),
                    nullableTimestamp(row.expiresAt()),
                    Timestamp.valueOf(row.dataTime()),
                    Timestamp.valueOf(row.dataTime()));
            return row.id();
        } catch (DuplicateKeyException duplicate) {
            // 影院使用与影片相同的唯一键竞争恢复策略，不能把并发重复初始化误判为失败。
            return requireCinemaId(row.sourceCinemaId(), duplicate);
        }
    }

    private Long findMovieId(String sourceMovieId) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                  FROM movie
                 WHERE source = ?
                   AND source_movie_id = ?
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), SEED_SOURCE, sourceMovieId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Long findCinemaId(String sourceCinemaId) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                  FROM cinema
                 WHERE source = ?
                   AND source_cinema_id = ?
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), SEED_SOURCE, sourceCinemaId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private long requireMovieId(String sourceMovieId, DuplicateKeyException cause) {
        Long movieId = findMovieId(sourceMovieId);
        if (movieId == null) {
            throw cause;
        }
        return movieId;
    }

    private long requireCinemaId(String sourceCinemaId, DuplicateKeyException cause) {
        Long cinemaId = findCinemaId(sourceCinemaId);
        if (cinemaId == null) {
            throw cause;
        }
        return cinemaId;
    }

    private Timestamp nullableTimestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
