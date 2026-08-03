package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * D 内容表的写入端口。
 *
 * <p>Application 只传递已经标准化的数据，不依赖 JDBC、SQL 或其他模块的 Mapper。影片和影院
 * 使用来源 ID 幂等查回既有主键；快照和同步日志保留数据库唯一键来阻止同一请求被重复记录。</p>
 */
public interface ContentPersistencePort {

    /** 保存或查回有非空来源 ID 的影片，返回本库实际主键。 */
    long ensureMovie(MovieRow row);

    /** 保存或查回有非空来源 ID 的影院，返回本库实际主键。 */
    long ensureCinema(CinemaRow row);

    /** 追加外部数据快照；相同 provider、externalId、dataType 由唯一键拒绝。 */
    void insertSnapshot(SnapshotRow row);

    /** 追加本次同步的统计日志；状态与数量关系同时由应用和数据库约束保护。 */
    void insertSyncLog(SyncLogRow row);

    /** 影片行只包含内容事实，不能携带场次、价格、座位或库存。 */
    record MovieRow(long id, String sourceMovieId, String title, String genresJson, int durationMinutes,
                    BigDecimal rating, ContentSourceType sourceType, String source,
                    LocalDateTime dataTime, LocalDateTime expiresAt) {
    }

    /** 影院行仅保存静态影院资料，影厅和排期仍属于 A 的票务模块。 */
    record CinemaRow(long id, String sourceCinemaId, String name, String cityCode, String area, String address,
                     BigDecimal longitude, BigDecimal latitude, ContentSourceType sourceType, String source,
                     LocalDateTime dataTime, LocalDateTime expiresAt) {
    }

    /** payloadJson 是最小必要原始载荷，不写入用户位置、密钥或票务事实。 */
    record SnapshotRow(long id, String provider, String externalId, String dataType, String payloadJson,
                       LocalDateTime dataTime, LocalDateTime expiresAt) {
    }

    /** 同步日志状态必须与完成时间和成功、失败统计一致。 */
    record SyncLogRow(long id, String provider, String resourceType, String requestId, SyncStatus status,
                      Integer errorCode, int totalCount, int successCount, int failureCount,
                      LocalDateTime startedAt, LocalDateTime finishedAt, String errorSummary) {
    }

    /** 数据库允许的同步状态，避免把任意字符串直接写入审计记录。 */
    enum SyncStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        PARTIAL
    }
}
