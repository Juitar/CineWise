package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ExternalShowtimeProvider;
import java.math.BigDecimal;
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
        if (!allowRequest()) {
            return new FetchOneResult(List.of(), FailureCategory.RATE_LIMITED);
        }
        try {
            return parse(showDate, externalCinema.externalCinemaId(), request(externalCinema));
        } catch (Exception firstFailure) {
            if (!retryable(firstFailure)) {
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
            return new Candidate(showId, movieId, cinemaId, startTime, price(show.path("vipPrice").asText(null)));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static BigDecimal price(String value) {
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

    private static FailureCategory classify(Exception exception) {
        if (exception instanceof RestClientResponseException response) {
            if (response.getStatusCode().value() == 429) {
                return FailureCategory.RATE_LIMITED;
            }
            return response.getStatusCode().is5xxServerError()
                    ? FailureCategory.UPSTREAM_5XX : FailureCategory.INVALID_DATA;
        }
        return exception instanceof ResourceAccessException ? FailureCategory.NETWORK : FailureCategory.INVALID_DATA;
    }

    private void pauseBeforeRetry() {
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
