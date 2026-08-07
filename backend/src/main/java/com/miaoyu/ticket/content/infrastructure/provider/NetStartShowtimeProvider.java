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
 * <p>Provider 城市 ID 只用于构造当前请求，不写入排期候选。</p>
 * <p>每个影院请求受共享滑动窗口保护，重试也消耗同一预算。</p>
 * <p>网络失败和 5xx 最多重试一次，429 和其他 4xx 不重试。</p>
 * <p>ResourceAccessException 会继续检查原因链，区分读超时和普通断网。</p>
 * <p>响应 success 或 data 结构不符合预期时归类为 INVALID_DATA。</p>
 * <p>上游返回多天资料时只接受调用方要求的业务日期。</p>
 * <p>缺少影片、影院、场次 ID 的记录直接隔离，不拼接名称和时间生成 ID。</p>
 * <p>showDate 与 tm 按 Asia/Shanghai 解析为 OffsetDateTime。</p>
 * <p>vipPrice 只保留非负数字作为参考价，缺失时返回 null。</p>
 * <p>影厅名称、语言和其他展示字段不进入候选模型。</p>
 * <p>Provider 返回的余座和座位字段即使存在也会被丢弃。</p>
 * <p>一次影院请求失败会让整个批次进入故障结果，避免实时和旧快照混合。</p>
 * <p>关闭 Provider 时不发起任何外部 HTTP 请求。</p>
 * <p>非 dev/demo profile 不允许访问学习用途数据源。</p>
 * <p>异常分类只输出固定枚举，不透传原始异常消息。</p>
 * <p>HTTP URI 不写入应用业务日志、快照或公开管理接口。</p>
 * <p>该适配器不访问 A 的票务 Mapper、Repository、Entity 或交易表。</p>
 * <p>请求路径固定为 /cinema/shows。</p>
 * <p>城市和影院参数来自已确认身份映射。</p>
 * <p>每个影院请求共享同一限流预算。</p>
 * <p>任一影院失败时整批失败，避免实时和旧数据混合。</p>
 * <p>429、不可重试 4xx 和字段错误不重试。</p>
 * <p>网络、超时和 5xx 最多重试一次。</p>
 * <p>重试会再次占用限流额度。</p>
 * <p>超时分类检查异常原因链。</p>
 * <p>无效响应只返回固定失败分类。</p>
 * <p>只接受请求日期的 show 节点。</p>
 * <p>时间统一按 Asia/Shanghai 解析。</p>
 * <p>缺少影片、影院或场次 ID 的记录会隔离。</p>
 * <p>seqNo 不与名称或时间拼接。</p>
 * <p>价格只作为参考价。</p>
 * <p>负价格被隔离。</p>
 * <p>余座和座位图字段不会进入 DTO。</p>
 * <p>影厅和语言文本不会进入 DTO。</p>
 * <p>原始响应只在适配器内存中短暂存在。</p>
 * <p>原始响应不会写日志或快照。</p>
 * <p>Key、Cookie、ci 和 URL 不进入公开模型。</p>
 * <p>Provider 关闭时不发起网络请求。</p>
 * <p>非 dev/demo 环境不访问学习用途数据源。</p>
 * <p>失败由 Application 负责快照降级。</p>
 * <p>本适配器不写 A 的票务事实。</p>
 * <p>请求失败不会清空 D 的快照。</p>
 * <p>限流拒绝不会建立 HTTP 连接。</p>
 * <p>连接超时和读取超时统一进入一次重试。</p>
 * <p>线程中断不会被吞掉。</p>
 * <p>响应 JSON 只读取必要字段。</p>
 * <p>上游多余字段自动忽略。</p>
 * <p>日期不匹配的场次被忽略。</p>
 * <p>时间解析失败的场次被忽略。</p>
 * <p>价格解析失败时保持空参考价。</p>
 * <p>成功响应也不意味着本地可售。</p>
 * <p>外部 Provider 不提供本地库存事实。</p>
 * <p>所有失败分类供 Application 选择降级策略。</p>
 * <p>不把异常类名返回给管理端。</p>
 * <p>该类只负责外部字段适配。</p>
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
