package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.content.application.ContentSyncTaskPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 MySQL/InnoDB 中资料事务取得租约行锁后，恢复器不能在资料提交前收敛同一任务。
 *
 * <p>默认跳过；只有显式连接专用 `cinewise_content_lease_check` 数据库时才执行，避免误操作日常库。</p>
 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_CONTENT_LEASE_MYSQL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "cinewise.seed.enabled=false",
        "cinewise.scheduling.enabled=false",
        "cinewise.content.netstart.enabled=false",
        "management.health.redis.enabled=false",
        "cinewise.auth.jwt-secret=content-lease-mysql-it-jwt-secret-at-least-32-bytes",
        "cinewise.auth.audit-hash-secret=content-lease-mysql-it-audit-secret-at-least-32-bytes"
})
class ContentSyncLeaseMySqlConcurrencyIntegrationTest {
    private static final String DATABASE = "cinewise_content_lease_check";
    private static final long SYNC_ID = 9_300_001L;
    private static final long SNAPSHOT_ID = 9_300_002L;
    private static final String REQUEST_ID = "content-lease-lock-it";
    private static final String OWNER = "content-lease-owner-it";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContentSyncTaskPort taskPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareDedicatedDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(DATABASE);
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        cleanup();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO data_sync_log (id, provider, resource_type, request_id, city_name, provider_city_id,
                    status, error_code, failure_category, lease_owner, lease_until, total_count, success_count,
                    failure_count, started_at, finished_at, error_summary, version, create_time, update_time)
                VALUES (?, 'NETSTART_MAOYAN', 'CURRENT_HOT_MOVIES', ?, '长沙', '70', 'RUNNING', NULL, NULL, ?, ?,
                    0, 0, 0, ?, NULL, NULL, 0, ?, ?)
                """, SYNC_ID, REQUEST_ID, OWNER, Timestamp.valueOf(now.plusSeconds(30)), Timestamp.valueOf(now),
                Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM external_data_snapshot WHERE id = ?", SNAPSHOT_ID);
        jdbcTemplate.update("DELETE FROM data_sync_log WHERE id = ?", SYNC_ID);
    }

    @Test
    void givenLeaseRowLockedInsideContentTransaction_whenRecoveryRuns_thenRollbackPreventsSnapshotCommit() {
        CountDownLatch recoveryEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> recovery = executor.submit(() -> {
            recoveryEntered.countDown();
            return failExpiredSyncTasksForTest(LocalDateTime.now().plusMinutes(2));
        });
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now();
                assertThat(taskPort.lockAndRenewActiveLease(SYNC_ID, OWNER, now.plusSeconds(90), now)).isTrue();
                jdbcTemplate.update("""
                        INSERT INTO external_data_snapshot (id, provider, external_id, data_type, payload_json,
                            data_time, expire_time, version, create_time, update_time)
                        VALUES (?, 'CONTENT_SNAPSHOT', 'lease-lock-it', 'MOVIE', '{}', ?, ?, 0, ?, ?)
                        """, SNAPSHOT_ID, Timestamp.valueOf(now), Timestamp.valueOf(now.plusHours(1)),
                        Timestamp.valueOf(now), Timestamp.valueOf(now));
                try {
                    assertThat(recoveryEntered.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                assertThat(recovery.isDone()).as("恢复器必须等待资料事务释放租约行锁").isFalse();
                throw new RollbackFixtureException();
            })).isInstanceOf(RollbackFixtureException.class);
            assertThat(recovery.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM external_data_snapshot WHERE id = ?", Integer.class, SNAPSHOT_ID)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM data_sync_log WHERE id = ?", String.class, SYNC_ID)).isEqualTo("FAILED");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 测试专用恢复调用，实际业务仍通过 ContentSyncRecoveryJob 进入同一端口。 */
    private int failExpiredSyncTasksForTest(LocalDateTime now) {
        return taskPort.failExpiredRunningTasks(now, 303004, ContentSyncTaskPort.FailureCategory.INTERNAL);
    }

    private static final class RollbackFixtureException extends RuntimeException { }
}
