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
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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

    /**
     * V014 影片资料在下一轮同步时必须原地更新；同一来源不能新增第二个 movieId，且来源本轮缺字段
     * 不能清掉已验证的海报、简介和上映资料。
     */
    @Test
    void givenExistingMovie_whenQualifiedFieldsChange_thenItUpdatesInPlaceAndKeepsMissingOptionalFields() {
        Assumptions.assumeTrue(hasV014MovieColumns(), "固定 H2 测试结构只执行到 V009；V014 写入由 MySQL 集成环境验证");
        long movieId = persistencePort.ensureMovie(movie(8_000_030L));
        MovieRow updated = new MovieRow(8_000_031L, "test-movie-001", "更新后的测试影片", "[\"科幻\"]", 130,
                new BigDecimal("9.1"), "https://example.test/new-poster.jpg", null, "NOW_SHOWING", null,
                ContentSourceType.LIVE, "test-provider", DATA_TIME.plusHours(1), DATA_TIME.plusHours(7));

        assertThat(persistencePort.ensureMovie(updated)).isEqualTo(movieId);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT title, genres_json, duration_minutes, rating, poster_url, summary, release_status, release_date,
                       version
                  FROM movie
                 WHERE id = ?
                """, movieId)).containsEntry("title", "更新后的测试影片")
                .containsEntry("genres_json", "[\"科幻\"]")
                .containsEntry("duration_minutes", 130)
                .containsEntry("poster_url", "https://example.test/new-poster.jpg")
                // 本轮没有简介和上映日期时，仍保留上一轮已经校验过的资料。
                .containsEntry("summary", "初始简介")
                .containsEntry("release_status", "NOW_SHOWING")
                .containsEntry("release_date", java.sql.Date.valueOf("2026-08-01"))
                .containsEntry("version", 1L);
    }

    @Test
    void givenV014CinemaColumns_whenCitySyncWritesThenItKeepsTheControlledCityOnInsertAndUpdate() {
        Assumptions.assumeTrue(hasV014CinemaColumns(), "固定 H2 测试结构只执行到 V009");
        long cinemaId = persistencePort.ensureCinema(cinema(8_000_040L), "上海", "10");
        CinemaRow updated = new CinemaRow(8_000_041L, "test-cinema-001", "更新后的影院", "10", "黄浦区",
                "更新地址", null, null, ContentSourceType.LIVE, "test-provider", DATA_TIME.plusHours(1),
                DATA_TIME.plusHours(7));

        assertThat(persistencePort.ensureCinema(updated, "上海", "10")).isEqualTo(cinemaId);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT name, city_name, provider_city_id, address, version FROM cinema WHERE id = ?
                """, cinemaId)).containsEntry("name", "更新后的影院")
                .containsEntry("city_name", "上海")
                .containsEntry("provider_city_id", "10")
                .containsEntry("address", "更新地址")
                .containsEntry("version", 1L);
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

    /**
     * 身份隔离后的同步日志按内容项写成一成一败，必须被真实数据库接受。
     * 这防止 Application Service 只在假端口测试中通过，却在 V004 CHECK 处回滚整次同步。
     */
    @Test
    void givenIdentityRejectedContent_whenPartialAuditIsInserted_thenDatabaseAcceptsConsistentCounts() {
        SyncLogRow identityRejected = new SyncLogRow(8_000_022L, "test-provider", "DAILY_CONTENT",
                "request-identity-rejected", SyncStatus.PARTIAL, null, 2, 1, 1, DATA_TIME,
                DATA_TIME.plusMinutes(1), "identityRejected=1;rejectionReason=IDENTITY_REVIEW_REQUIRED");

        persistencePort.insertSyncLog(identityRejected);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_sync_log WHERE request_id = 'request-identity-rejected'", Long.class))
                .isOne();
    }

    private MovieRow movie(long id) {
        return new MovieRow(id, "test-movie-001", "测试影片", "[\"剧情\"]", 120, new BigDecimal("8.5"),
                "https://example.test/initial-poster.jpg", "初始简介", "COMING_SOON", LocalDate.of(2026, 8, 1),
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

    /** 只有真实 V014 结构才验证新列，避免固定 V009 H2 基线伪造已完成的 MySQL 验证。 */
    private boolean hasV014MovieColumns() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 'MOVIE' AND COLUMN_NAME = 'POSTER_URL'
                """, Integer.class);
        return count != null && count > 0;
    }

    private boolean hasV014CinemaColumns() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 'CINEMA' AND COLUMN_NAME = 'CITY_NAME'
                """, Integer.class);
        return count != null && count > 0;
    }
}
