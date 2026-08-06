package com.miaoyu.ticket.travel.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.travel.application.WeatherObservation;
import com.miaoyu.ticket.travel.application.WeatherProvider;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 高德实况天气适配器。
 *
 * <p>高德的天气粒度是行政区，不是用户当前位置。影院区域没有登记行政区码、未配置 key 或接口失败时都返回空，
 * 由 {@link com.miaoyu.ticket.travel.application.WeatherQueryService} 统一选择缓存、Demo 和不可用结果。</p>
 *
 * <p>这个 Provider 不保存高德原始响应。</p>
 *
 * <p>任务快照只保存已经标准化的天气状况和提醒文字。</p>
 *
 * <p>因此用户不会因为查看天气建议而暴露精确位置。</p>
 */
public final class AmapWeatherProvider implements WeatherProvider {
    static final String SOURCE = "AMAP_WEATHER";
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AmapWeatherProperties properties;
    private final AmapWeatherClient client;

    public AmapWeatherProvider(AmapWeatherProperties properties, AmapWeatherClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 仅在显式开启且具备 key、区域码时发起网络调用。 */
    @Override
    public Optional<WeatherObservation> query(String cinemaArea, OffsetDateTime requestedAt) {
        if (!properties.enabled() || properties.key().isBlank()) {
            // 未配置密钥时不请求第三方，保持离线演示可用。
            return Optional.empty();
        }
        String adcode = properties.findAdcode(cinemaArea);
        if (adcode == null) {
            // 不猜测行政区码，避免把同名区域或城市级天气误用于影院。
            return Optional.empty();
        }
        try {
            // 原始响应只在当前调用栈解析，不进入缓存、快照或日志。
            return toObservation(cinemaArea, requestedAt, client.query(adcode, properties.key()));
        } catch (RuntimeException exception) {
            // 不记录请求参数或异常消息，避免 key 被带入日志；上层会继续执行既定回退。
            return Optional.empty();
        }
    }

    private Optional<WeatherObservation> toObservation(
            String cinemaArea, OffsetDateTime requestedAt, JsonNode response) {
        // status 非成功或没有实况数组，都不能视为可展示的真实天气。
        if (!"1".equals(response.path("status").asText()) || !response.path("lives").isArray()
                || response.path("lives").isEmpty()) {
            return Optional.empty();
        }
        JsonNode live = response.path("lives").get(0);
        String weather = text(live, "weather");
        if (weather == null) {
            // 没有天气现象时不拼接半成品建议，交给统一回退处理。
            return Optional.empty();
        }
        String temperature = text(live, "temperature");
        // reporttime 是高德提供的数据时间；缺失或格式变化时使用本次请求时间。
        OffsetDateTime dataTime = parseReportTime(text(live, "reporttime"), requestedAt);
        String condition = temperature == null ? weather : weather + "（" + temperature + "℃）";
        // 有效期从请求时刻计算，避免上游 reporttime 较旧时刚写入就被当成过期。
        return Optional.of(new WeatherObservation(cinemaArea, condition, travelAdvice(weather), SOURCE, dataTime,
                requestedAt.plus(properties.cacheTtl()), false, false, null));
    }

    /**
     * 把高德天气现象转换为固定出行提醒。
     *
     * <p>雷雨、雨雪和低能见度优先提示安全。</p>
     *
     * <p>普通天气只提示预留到场时间。</p>
     *
     * <p>规则固定且可测试，不根据用户画像推断出行方式。</p>
     */
    private String travelAdvice(String weather) {
        if (weather.contains("雷") || weather.contains("暴雨") || weather.contains("大雨")) {
            return "可能有强降水或雷电，请提前出发并注意出行安全";
        }
        if (weather.contains("雨") || weather.contains("雪")) {
            return "可能有降水，请携带雨具并预留更多到场时间";
        }
        if (weather.contains("雾") || weather.contains("霾")) {
            return "能见度可能较低，请提前出发并注意交通安全";
        }
        return "请预留充足到场时间，并在出发前再次确认天气";
    }

    private OffsetDateTime parseReportTime(String reportTime, OffsetDateTime fallback) {
        if (reportTime == null) {
            // 高德偶发缺少上报时间时仍可展示本次成功结果，但标注时间为查询时刻。
            return fallback;
        }
        try {
            return LocalDateTime.parse(reportTime, REPORT_TIME_FORMAT)
                    .atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            // 上游时间格式变化不影响购票和 Demo 回退，只降级为请求时间。
            return fallback;
        }
    }

    private String text(JsonNode node, String fieldName) {
        // 统一去除空白，避免把空字符串当成有效天气字段。
        String value = node.path(fieldName).asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
