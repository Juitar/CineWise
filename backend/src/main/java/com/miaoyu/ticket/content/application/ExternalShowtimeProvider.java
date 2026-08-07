package com.miaoyu.ticket.content.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** D 内部的排期 Provider 边界，Application 不依赖 HTTP、JsonNode 或上游状态码。 */
public interface ExternalShowtimeProvider {

    /**
     * 按业务日期批量读取影院的排期候选。
     *
     * <p>返回失败分类时，调用方只能读取未过期快照；不能把失败转换为空排期，也不能重试第二个 Provider。</p>
     */
    FetchResult fetch(LocalDate showDate, List<ExternalCinema> externalCinemas);

    /** Provider 失败类型仅用于快照降级与脱敏审计，不能透传原始响应。 */
    enum FailureCategory { PROVIDER_DISABLED, RATE_LIMITED, TIMEOUT, NETWORK, UPSTREAM_5XX, INVALID_DATA }

    record FetchResult(List<Candidate> candidates, FailureCategory failureCategory) {
        public FetchResult {
            // Provider 适配器先完成字段过滤，Application 层拿到的列表不会再引用可变上游集合。
            candidates = List.copyOf(candidates);
        }

        /** null 失败分类才代表本次实时读取完整成功。 */
        public boolean available() { return failureCategory == null; }
    }

    /** Provider Mapper 已丢弃余座、座位、订单和支付字段，只保留标准化的候选资料。 */
    /** 候选不包含余座、座位图、订单或支付字段，listedPrice 永远只是参考价。 */
    record Candidate(String externalShowId, String externalMovieId, String externalCinemaId,
                     OffsetDateTime startTime, OffsetDateTime endTime, BigDecimal listedPrice,
                     Integer durationMinutes, String auditoriumText) {
        /** 兼容没有沙箱参考字段的 Provider 测试夹具。 */
        public Candidate(String externalShowId, String externalMovieId, String externalCinemaId,
                         OffsetDateTime startTime, OffsetDateTime endTime, BigDecimal listedPrice) {
            this(externalShowId, externalMovieId, externalCinemaId, startTime, endTime, listedPrice, null, null);
        }
    }

    /** Provider 城市 ID 只在 D 的 HTTP 适配器使用，A 的公开 Port 不会得到该字段。 */
    record ExternalCinema(String externalCinemaId, String providerCityId) { }
}
