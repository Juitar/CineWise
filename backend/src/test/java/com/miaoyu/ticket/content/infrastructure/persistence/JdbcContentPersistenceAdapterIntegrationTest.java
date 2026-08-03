package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.content.application.ContentPersistencePort;
import com.miaoyu.ticket.content.application.ContentPersistencePort.CinemaRow;
import com.miaoyu.ticket.content.application.ContentPersistencePort.MovieRow;
import com.miaoyu.ticket.content.application.ContentPersistencePort.SnapshotRow;
import com.miaoyu.ticket.content.application.ContentPersistencePort.SyncLogRow;
import com.miaoyu.ticket.content.application.ContentPersistencePort.SyncStatus;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class JdbcContentPersistenceAdapterIntegrationTest {

    private static final LocalDateTime DATA_TIME = LocalDateTime.of(2026, 8, 3, 9, 0);

    @Autowired
    private ContentPersistencePort persistencePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenSameSourceIdentity_whenEnsuringMovieAndCinemaTwice_thenExistingIdsAreReused() {
        long movieId = persistencePort.ensureMovie(movie(8_000_001L));
        long cinemaId = persistencePort.ensureCinema(cinema(8_000_002L));

        // 重复初始化只能查回既有内容 ID，不能让 A 已关联的场次指向第二条内容记录。
        assertThat(persistencePort.ensureMovie(movie(8_000_003L))).isEqualTo(movieId);
        assertThat(persistencePort.ensureCinema(cinema(8_000_004L))).isEqualTo(cinemaId);
        assertThat(count("movie", "test-provider", "test-movie-001")).isOne();
        assertThat(count("cinema", "test-provider", "test-cinema-001")).isOne();
    }

    @Test
    void givenDuplicateSnapshotOrSyncRequest_whenInserted_thenDatabaseKeepsOnlyTheFirstAuditRecord() {
        SnapshotRow snapshot = new SnapshotRow(8_000_010L, "test-provider", "external-001", "MOVIE",
                "{\"title\":\"测试影片\"}", DATA_TIME, DATA_TIME.plusHours(6));
        persistencePort.insertSnapshot(snapshot);
        persistencePort.insertSyncLog(successLog(8_000_011L, "request-001"));

        // 快照和同步日志不能静默覆盖；调用方必须按原外部 ID 或 requestId 恢复结果。
        assertThatThrownBy(() -> persistencePort.insertSnapshot(snapshot))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> persistencePort.insertSyncLog(successLog(8_000_012L, "request-001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenInvalidSnapshotTimeOrFinishedLogCounts_whenInserted_thenDatabaseCheckRejectsThem() {
        SnapshotRow expiredBeforeData = new SnapshotRow(8_000_020L, "test-provider", "external-002", "MOVIE",
                "{}", DATA_TIME, DATA_TIME.minusSeconds(1));
        SyncLogRow inconsistentSuccess = new SyncLogRow(8_000_021L, "test-provider", "MOVIE", "request-002",
                SyncStatus.SUCCESS, null, 2, 1, 1, DATA_TIME, DATA_TIME.minusSeconds(1), null);

        // 这些约束必须由 V004 在数据库层执行，避免绕过应用服务的写入留下不可用审计数据。
        assertThatThrownBy(() -> persistencePort.insertSnapshot(expiredBeforeData))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> persistencePort.insertSyncLog(inconsistentSuccess))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MovieRow movie(long id) {
        return new MovieRow(id, "test-movie-001", "测试影片", "[\"剧情\"]", 120, new BigDecimal("8.5"),
                ContentSourceType.MOCK, "test-provider", DATA_TIME, DATA_TIME.plusHours(6));
    }

    private CinemaRow cinema(long id) {
        return new CinemaRow(id, "test-cinema-001", "测试影城", "310000", "黄浦区", "测试路 1 号",
                new BigDecimal("121.4737000"), new BigDecimal("31.2304000"), ContentSourceType.MOCK,
                "test-provider", DATA_TIME, DATA_TIME.plusHours(6));
    }

    private SyncLogRow successLog(long id, String requestId) {
        return new SyncLogRow(id, "test-provider", "MOVIE", requestId, SyncStatus.SUCCESS, null,
                2, 2, 0, DATA_TIME, DATA_TIME.plusMinutes(1), null);
    }

    private long count(String table, String source, String sourceId) {
        String column = "movie".equals(table) ? "source_movie_id" : "source_cinema_id";
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE source = ? AND " + column + " = ?",
                Long.class, source, sourceId);
    }
}
