package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Map;

/**
 * D 内容表的写入端口。
 *
 * <p>Application 只传递已经标准化的数据，不依赖 JDBC、SQL 或其他模块的 Mapper。影片和影院
 * 使用来源 ID 幂等查回既有主键；快照和同步日志保留数据库唯一键来阻止同一请求被重复记录。</p>
 */
public interface ContentPersistencePort {

    /**
     * 保存或更新有非空来源 ID 的影片，返回本库实际主键。
     *
     * <p>重复同步必须保留既有业务 ID，避免 A 已关联的场次失去影片引用；可选展示字段为 null 时表示
     * 本次来源没有提供，不能据此清空已验证资料。</p>
     */
    long ensureMovie(MovieRow row);

    /** 保存或查回有非空来源 ID 的影院，返回本库实际主键。 */
    long ensureCinema(CinemaRow row);

    /**
     * 城市同步必须把受控城市名称和 Provider 城市编号一同写入影院资料。
     *
     * <p>默认实现保留给 V014 前的测试夹具使用；正式 JDBC 实现会覆盖它，避免把 Provider 返回的
     * cityCode 当作可公开展示的城市名称。</p>
     */
    default long ensureCinema(CinemaRow row, String cityName, String cityCode) {
        return ensureCinema(row);
    }

    /** 追加外部数据快照；相同 provider、externalId、dataType 由唯一键拒绝。 */
    void insertSnapshot(SnapshotRow row);

    /** 追加本次同步的统计日志；状态与数量关系同时由应用和数据库约束保护。 */
    void insertSyncLog(SyncLogRow row);

    /**
     * 返回本次目录中已经成功写入本地影片表的来源身份。
     *
     * <p>仅按来源和外部 ID 判断，绝不按标题猜测。未命中的身份仍需由后续受控批次请求详情，
     * 因而不会把未完成项当作可以公开浏览的影片。</p>
     */
    default Set<String> findExistingMovieSourceIds(String source) {
        // 旧的测试夹具和 V014 前的受控实现没有目录恢复查询；返回空集合只会多做幂等详情更新，
        // 不会把尚未成功的身份写入公开目录。正式 JDBC 适配器会覆盖为真实的已完成身份查询。
        return Set.of();
    }

    /** 返回已落库影片的上映资料，用于只在 Provider 资料发生变化时重新拉取详情。 */
    default Map<String, MovieState> findExistingMovieStates(String source) {
        return Map.of();
    }

    /** dataTime 用于每日有限预算内轮换复查详情，避免资料永远只按上映状态判断。 */
    record MovieState(String releaseDate, String releaseStatus, LocalDateTime dataTime) {
        /** 兼容旧测试夹具；未提供同步时间时保留原有“状态相同则跳过”的语义。 */
        public MovieState(String releaseDate, String releaseStatus) {
            this(releaseDate, releaseStatus, null);
        }
    }

    /** 影片行只包含内容事实，不能携带场次、价格、座位或库存。 */
    record MovieRow(long id, String sourceMovieId, String title, String genresJson, int durationMinutes,
                    BigDecimal rating, String posterUrl, String summary, String releaseStatus, LocalDate releaseDate,
                    ContentSourceType sourceType, String source,
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
                      LocalDateTime startedAt, LocalDateTime finishedAt, String errorSummary,
                      String cityName, String cityCode) {
        /** 兼容 V004/V009 测试夹具；城市同步会使用带城市字段的完整构造器。 */
        public SyncLogRow(long id, String provider, String resourceType, String requestId, SyncStatus status,
                          Integer errorCode, int totalCount, int successCount, int failureCount,
                          LocalDateTime startedAt, LocalDateTime finishedAt, String errorSummary) {
            this(id, provider, resourceType, requestId, status, errorCode, totalCount, successCount, failureCount,
                    startedAt, finishedAt, errorSummary, null, null);
        }
    }

    /** 数据库允许的同步状态，避免把任意字符串直接写入审计记录。 */
    enum SyncStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        PARTIAL
    }
}
