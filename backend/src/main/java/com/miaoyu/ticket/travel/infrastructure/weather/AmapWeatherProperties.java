package com.miaoyu.ticket.travel.infrastructure.weather;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 高德天气的运行配置。
 *
 * <p>高德接口要求传行政区码，不接受影院名称作为稳定的查询标识。因此区域到行政区码的映射由部署配置维护，
 * 不能从用户输入、用户地址或路线数据临时推断。</p>
 *
 * <p>key 只从环境变量读取。</p>
 *
 * <p>key 不会被本对象输出到日志。</p>
 *
 * <p>新增影院区域时，需要由内容数据负责人提供对应行政区码。</p>
 *
 * <p>没有区域码时不发起高德请求，避免错误地查询到相邻区域天气。</p>
 */
@ConfigurationProperties("cinewise.travel.weather.amap")
public record AmapWeatherProperties(boolean enabled, String key, Duration cacheTtl,
                                   Map<String, String> areaAdcodes, Map<String, String> cityAdcodes) {

    /** 保留旧构造方式，避免已有单元测试和离线夹具因为新增城市配置被迫改动。 */
    public AmapWeatherProperties(boolean enabled, String key, Duration cacheTtl,
                                 Map<String, String> areaAdcodes) {
        this(enabled, key, cacheTtl, areaAdcodes, Map.of());
    }

    /**
     * 启动时拒绝无效有效期和错误行政区码。
     *
     * <p>这样错误配置会在部署时暴露，而不是在用户刷新出行建议时才触发。</p>
     *
     * <p>行政区码固定为六位正整数，不接受影院内部 ID。</p>
     */
    @ConstructorBinding
    public AmapWeatherProperties {
        key = key == null ? "" : key.trim();
        cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        Objects.requireNonNull(areaAdcodes, "areaAdcodes must not be null").forEach((area, adcode) -> {
            normalized.put(requireText(area, "area"), requireAdcode(adcode));
        });
        areaAdcodes = Map.copyOf(normalized);
        Map<String, String> normalizedCities = new LinkedHashMap<>();
        cityAdcodes = cityAdcodes == null ? Map.of() : cityAdcodes;
        cityAdcodes.forEach((city, adcode) ->
                normalizedCities.put(requireText(city, "city"), requireAdcode(adcode)));
        cityAdcodes = Map.copyOf(normalizedCities);
    }

    /**
     * 缺少映射时不猜测城市编码。
     *
     * <p>调用方会回退到 Demo 或不可用结果。</p>
     */
    public String findAdcode(String cinemaArea) {
        if (cinemaArea == null) {
            return null;
        }
        String normalizedArea = cinemaArea.trim();
        // 逆地理 Adapter 已返回合法 adcode 时直接使用，不能再要求它匹配展示区域名称。
        if (normalizedArea.matches("[1-9][0-9]{5}")) {
            return normalizedArea;
        }
        String areaCode = areaAdcodes.get(normalizedArea);
        if (areaCode != null) {
            return areaCode;
        }
        // 区域没有精确配置时，只按已登记的城市名称查找，不从地址文本推测行政区码。
        return cityAdcodes.get(normalizedArea);
    }

    private static String requireAdcode(String adcode) {
        String normalized = requireText(adcode, "adcode");
        if (!normalized.matches("[1-9][0-9]{5}")) {
            throw new IllegalArgumentException("adcode must be a six digit administrative code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
