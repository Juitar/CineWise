package com.miaoyu.ticket.content.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** D 内部的排期 Provider 边界，Application 不依赖 HTTP、JsonNode 或上游状态码。 */
public interface ExternalShowtimeProvider {

    FetchResult fetch(LocalDate showDate, List<ExternalCinema> externalCinemas);

    /** Provider 失败类型仅用于快照降级与脱敏审计，不能透传原始响应。 */
    enum FailureCategory { PROVIDER_DISABLED, RATE_LIMITED, TIMEOUT, NETWORK, UPSTREAM_5XX, INVALID_DATA }

    record FetchResult(List<Candidate> candidates, FailureCategory failureCategory) {
        public FetchResult {
            candidates = List.copyOf(candidates);
        }

        public boolean available() { return failureCategory == null; }
    }

    /** Provider Mapper 已丢弃余座、座位、订单和支付字段，只保留标准化的候选资料。 */
    record Candidate(String externalShowId, String externalMovieId, String externalCinemaId,
                     OffsetDateTime startTime, BigDecimal listedPrice) { }

    /** Provider 城市 ID 只在 D 的 HTTP 适配器使用，A 的公开 Port 不会得到该字段。 */
    record ExternalCinema(String externalCinemaId, String providerCityId) { }
}
