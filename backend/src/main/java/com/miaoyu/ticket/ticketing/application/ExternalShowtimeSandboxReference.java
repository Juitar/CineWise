package com.miaoyu.ticket.ticketing.application;

import java.time.OffsetDateTime;

/**
 * A 本地沙箱导入使用的外部排期参考输入。
 *
 * <p>该类型故意不依赖 D 的公开 DTO。未来适配器只负责显式映射，避免 D 的 Provider 或快照实现
 * 渗入票务模块；所有影厅、座位、库存和交易价格仍由 A 在本地创建。</p>
 */
public record ExternalShowtimeSandboxReference(
        String provider,
        String source,
        String externalCinemaId,
        String externalShowId,
        Long movieId,
        Long cinemaId,
        OffsetDateTime startTime,
        Integer durationMinutes,
        String auditoriumText,
        OffsetDateTime dataAt,
        OffsetDateTime expiresAt,
        boolean sandboxReferenceEligible,
        boolean expired,
        boolean degraded,
        boolean fallback) {

    /**
     * 兼容仅提供身份信息的测试构造；生产映射必须使用 D 返回的 source。
     */
    public ExternalShowtimeSandboxReference(String provider, String externalCinemaId, String externalShowId,
            Long movieId, Long cinemaId, OffsetDateTime startTime, Integer durationMinutes, String auditoriumText,
            OffsetDateTime dataAt, OffsetDateTime expiresAt, boolean sandboxReferenceEligible, boolean expired,
            boolean degraded, boolean fallback) {
        this(provider, provider, externalCinemaId, externalShowId, movieId, cinemaId, startTime, durationMinutes,
                auditoriumText, dataAt, expiresAt, sandboxReferenceEligible, expired, degraded, fallback);
    }
}
