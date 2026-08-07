package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ExternalShowtimeProvider;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * NetStart 的排期候选适配器。
 *
 * <p>它只读取 `/cinema/shows` 并立刻丢弃原始 JSON；余座、座位、订单和支付字段没有进入任何 DTO。</p>
 * <p>真实 Provider 只在 dev/demo 且显式启用时调用，其他环境直接返回关闭状态。</p>
 */
public final class NetStartShowtimeProvider implements ExternalShowtimeProvider {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final NetStartProperties properties;
    private final Environment environment;
    private final Clock clock;
    private final RestClient restClient;
    private final NetStartRequestLimiter requestLimiter;

    public NetStartShowtimeProvider(NetStartProperties properties, Environment environment, Clock clock,
                                    RestClient restClient) {
        this(properties, environment, clock, restClient, new NetStartRequestLimiter(properties, clock));
    }

    NetStartShowtimeProvider(NetStartProperties properties, Environment environment, Clock clock,
                             RestClient restClient, NetStartRequestLimiter requestLimiter) {
        this.properties = properties;
        this.environment = environment;
        this.clock = clock;
        this.restClient = restClient;
        this.requestLimiter = requestLimiter;
    }

    @Override
    /**
     * 一个影院失败即回退整批查询。
     *
     * <p>这样 A 不会把不完整的实时列表当作完整排期导入；失败时由 Application 统一决定是否读取快照。</p>
     */
    public FetchResult fetch(LocalDate showDate, List<ExternalCinema> externalCinemas) {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return new FetchResult(List.of(), FailureCategory.PROVIDER_DISABLED);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (ExternalCinema externalCinema : externalCinemas) {
            FetchOneResult result = fetchOne(showDate, externalCinema);
            if (result.failureCategory() != null) {
                // 同一次查询不能把部分影院的实时结果和其他影院的旧快照混在一起，避免 A 误以为数据完整。
                return new FetchResult(List.of(), result.failureCategory());
            }
            candidates.addAll(result.candidates());
        }
        return new FetchResult(candidates, null);
    }

    private FetchOneResult fetchOne(LocalDate showDate, ExternalCinema externalCinema) {
        // 每次尝试都先占用共享预算，重试不能绕过影片、影院和排期共用的 10 req/min 限制。
        if (!allowRequest()) {
            return new FetchOneResult(List.of(), FailureCategory.RATE_LIMITED);
        }
        try {
            return parse(showDate, externalCinema.externalCinemaId(), request(externalCinema));
        } catch (Exception firstFailure) {
            if (!retryable(firstFailure)) {
                // 429、普通 4xx 和字段错误不会重试，避免无效调用扩大上游压力。
                return new FetchOneResult(List.of(), classify(firstFailure));
            }
            pauseBeforeRetry();
            if (!allowRequest()) {
                return new FetchOneResult(List.of(), FailureCategory.RATE_LIMITED);
            }
            try {
                return parse(showDate, externalCinema.externalCinemaId(), request(externalCinema));
            } catch (Exception retryFailure) {
                return new FetchOneResult(List.of(), classify(retryFailure));
            }
        }
    }

    private JsonNode request(ExternalCinema externalCinema) {
        // ci 仅在请求 URI 中短暂使用，日志、快照和公开 DTO 都不会保存该内部城市编号。
        return restClient.get().uri(uri -> uri.path("/cinema/shows")
                .queryParam("ci", externalCinema.providerCityId())
                .queryParam("cinemaId", externalCinema.externalCinemaId()).build()).retrieve().body(JsonNode.class);
    }

    private FetchOneResult parse(LocalDate expectedDate, String externalCinemaId, JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false) || response.path("data").isMissingNode()) {
            return new FetchOneResult(List.of(), FailureCategory.INVALID_DATA);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (JsonNode movie : response.path("data").path("movies")) {
            String movieId = text(movie, "id");
            for (JsonNode shows : movie.path("shows")) {
                // 上游按影院返回多天资料时，只接受调用方请求的业务日期。
                if (!expectedDate.toString().equals(text(shows, "showDate"))) {
                    continue;
                }
                for (JsonNode show : shows.path("plist")) {
                    Candidate candidate = candidate(movieId, externalCinemaId, expectedDate, show);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return new FetchOneResult(candidates, null);
    }

    private Candidate candidate(String movieId, String cinemaId, LocalDate date, JsonNode show) {
        String showId = text(show, "seqNo");
        String time = text(show, "tm");
        if (blank(movieId) || blank(cinemaId) || blank(showId) || blank(time)) {
            return null;
        }
        try {
            OffsetDateTime startTime = LocalDateTime.parse(date + " " + time, DATE_TIME)
                    .atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
            // seqNo 只在 provider + cinemaId 范围内使用；Application 会把三元组公开给 A 作为导入幂等键。
            return new Candidate(showId, movieId, cinemaId, startTime, price(show.path("vipPrice").asText(null)));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static BigDecimal price(String value) {
        // 标价为缺失或负数时保持 null；A 使用自己的沙箱价格，不能补造交易金额。
        if (blank(value)) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.signum() >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private boolean isLearningEnvironment() {
        // 防止测试配置误带到测试、预发或生产环境后实际访问学习用途 Provider。
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile) || "demo".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowRequest() { return requestLimiter.allowRequest(); }

    private static boolean retryable(Exception exception) {
        return exception instanceof ResourceAccessException || exception instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }

    static FailureCategory classify(Exception exception) {
        if (exception instanceof RestClientResponseException response) {
            if (response.getStatusCode().value() == 429) {
                return FailureCategory.RATE_LIMITED;
            }
            return response.getStatusCode().is5xxServerError()
                    ? FailureCategory.UPSTREAM_5XX : FailureCategory.INVALID_DATA;
        }
        if (exception instanceof ResourceAccessException && containsTimeout(exception)) {
            return FailureCategory.TIMEOUT;
        }
        return exception instanceof ResourceAccessException ? FailureCategory.NETWORK : FailureCategory.INVALID_DATA;
    }

    /** Spring 会把连接、读超时包装成 ResourceAccessException；继续检查原因链才能和普通断网区分。 */
    private static boolean containsTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private void pauseBeforeRetry() {
        // 仅在允许重试的网络或 5xx 失败后等待一次，Interrupted 时保留中断标记。
        try {
            Thread.sleep(properties.retryBackoff());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record FetchOneResult(List<Candidate> candidates, FailureCategory failureCategory) { }
}
