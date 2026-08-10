package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 NetStart 原始 JSON 隔离在基础设施层并转换为 D 的内容模型。
 *
 * <p>只读取影片、影院基础字段；影评、海报、场次、价格和座位等字段即使上游返回也不会进入
 * 标准化对象。外部 ID 缺失或最低字段不完整时直接拒绝，后续同步只能记录质量问题，不能覆盖现有内容。</p>
 */
/**
 * 将 NetStart 的不稳定响应收缩为 D 可持久化的影片、影院基础字段。
 *
 * <p>Mapper 不返回原始 JSON，也不透传上游可能夹带的影评、场次、价格、库存、座位或订单字段。
 * 这些内容不是 D 的基础资料，尤其不能覆盖 A 的票务事实。</p>
 *
 * <p>外部 ID、标题或名称等最低字段不足时直接返回空，由 Provider 将该条作为字段不合格处理。
 * Mapper 不做同名自动合并；跨请求的重复和冲突由 ContentIdentityPolicy 在写入前隔离。</p>
 *
 * <p>分类必须以 JSON 数组保存，供公开 REST 层稳定还原为数组；未知经纬度保留 null，
 * 避免把“上游未提供位置”伪造成位于赤道和本初子午线交点。</p>
 *
 * <p>这里不对地址做地理编码或坐标补全，避免网络调用与不确定推断混进每日内容同步。
 * 出行模块若需要路线，仅可在用户当前请求中通过自己的受控端口处理。</p>
 */
final class NetStartContentMapper {

    /**
     * 地址中的行政区域由受控清单解析，避免“住宅区”等地点名称被错误写入影院资料。
     * 解析规则属于 Provider 字段标准化，不能散落到前端或天气模块重复处理。
     */
    private final CinemaAdministrativeAreaResolver areaResolver = new CinemaAdministrativeAreaResolver();

    Optional<ContentItem> map(ContentResourceType type, JsonNode raw, String cityCode) {
        return type == ContentResourceType.MOVIE ? mapMovie(raw) : mapCinema(raw, cityCode);
    }

    List<ContentItem> mapAll(ContentResourceType type, JsonNode raw, String cityCode) {
        JsonNode items = type == ContentResourceType.MOVIE ? raw.path("movieList") : raw;
        List<ContentItem> mapped = new ArrayList<>();
        if (items.isArray()) { items.forEach(item -> map(type, item, cityCode).ifPresent(mapped::add)); }
        else { map(type, raw, cityCode).ifPresent(mapped::add); }
        return List.copyOf(mapped);
    }

    private Optional<ContentItem> mapMovie(JsonNode raw) {
        // 旧接口使用 detailMovie；当前页面详情接口使用 movie。两种响应都只取最低基础字段，
        // 不能因包装层调整把本来合格的影片误判为字段缺失，也不能把整份页面响应向上透传。
        JsonNode movie = movieNode(raw);
        return text(movie, "id").flatMap(id -> text(movie, "nm").flatMap(title -> text(movie, "cat")
                .flatMap(genres -> positiveInt(movie, "dur").flatMap(duration -> decimal(movie, "sc")
                        .map(rating -> new MovieContent(null, id, title, genresJson(genres), duration, rating,
                                // 海报、简介和上映字段都在 Adapter 收缩，后续层不接触 NetStart 原始字段名。
                                httpsUrl(movie.path("img").asText(null)), optionalText(movie, "dra"),
                                optionalText(movie, "rt"), releaseStatus(movie)))))));
    }

    /** 详情字段不存在时保留直接对象兼容，方便受控夹具只描述影片本身而不复制页面外层结构。 */
    private JsonNode movieNode(JsonNode raw) {
        if (raw.path("detailMovie").isObject()) {
            return raw.path("detailMovie");
        }
        return raw.path("movie").isObject() ? raw.path("movie") : raw;
    }

    private Optional<ContentItem> mapCinema(JsonNode raw, String cityCode) {
        JsonNode info = raw.path("info").isObject() ? raw.path("info") : raw;
        return text(raw, "id").flatMap(id -> text(info, "name").flatMap(name -> text(info, "address")
                .flatMap(address -> text(cityCode).map(city -> new CinemaContent(id, name, city,
                        areaFromAddress(city, address), address, coordinate(raw, "lng", "longitude", 180),
                        coordinate(raw, "lat", "latitude", 90))))));
    }

    Optional<CinemaContent> mapCinemaDetail(JsonNode raw, String cityCode) {
        JsonNode data = raw.path("data").isObject() ? raw.path("data") : raw;
        return text(data, "cinemaId").flatMap(id -> text(data, "nm").flatMap(name -> text(data, "addr")
                .flatMap(address -> text(cityCode).map(city -> new CinemaContent(id, name, city,
                        areaFromAddress(city, address), address, coordinate(data, "lng", "longitude", 180),
                        coordinate(data, "lat", "latitude", 90))))));
    }

    private Optional<String> text(JsonNode node, String field) { return text(node.path(field).asText(null)); }
    private Optional<String> text(String value) {
        if (value == null || value.trim().isEmpty()) { return Optional.empty(); }
        return Optional.of(value.trim());
    }
    /** 可选字段缺失时返回 null，让 REST 明确表达“来源未提供”，而不是用猜测文本补全。 */
    private String optionalText(JsonNode node, String field) { return text(node, field).orElse(null); }

    /**
     * NetStart 的海报可能是 HTTP、相对路径或异常文本；这些地址不会越过 Provider 适配层。
     */
    private String httpsUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri.normalize().toString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 上游只有全局上映标记时，保留受控枚举，不用场次或票务字段推断影片状态。 */
    /** 仅输出 C 已确认的两个状态；未知上游形态保持 null，不能泄漏任意 Provider 原值。 */
    private String releaseStatus(JsonNode movie) {
        if (movie.path("globalReleased").asBoolean(false)) {
            return "NOW_SHOWING";
        }
        return text(movie, "rt").isPresent() ? "COMING_SOON" : null;
    }
    private Optional<Integer> positiveInt(JsonNode node, String field) {
        String value = node.path(field).asText("").replaceAll("[^0-9]", "");
        try {
            return value.isEmpty() || Integer.parseInt(value) <= 0
                    ? Optional.empty() : Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException exception) { return Optional.empty(); }
    }
    private Optional<BigDecimal> decimal(JsonNode node, String field) {
        try { return Optional.of(new BigDecimal(node.path(field).asText(""))); }
        catch (NumberFormatException exception) { return Optional.empty(); }
    }
    /** 经纬度只接受上游明确提供且落在地理范围内的静态坐标，不能由地址或距离文本反推。 */
    private BigDecimal coordinate(JsonNode node, String shortField, String longField, int maximumAbsoluteValue) {
        Optional<BigDecimal> value = decimal(node, shortField).or(() -> decimal(node, longField));
        return value.filter(item -> item.abs().compareTo(BigDecimal.valueOf(maximumAbsoluteValue)) <= 0).orElse(null);
    }
    /**
     * Provider 未提供独立区域字段时，只从当前同步城市已登记的行政区名称中匹配。
     * 未命中不做后缀猜测，既保证“浏阳市”等县级市可用，也避免把商圈名称写成行政区。
     */
    private String areaFromAddress(String cityCode, String address) {
        return areaResolver.resolve(cityCode, address);
    }
    /**
     * 上游逗号分隔分类在写入 JSON 列前转为数组，公开接口才能稳定返回 genres 数组。
     *
     * <p>分隔符和引号都在此处规范化，避免后续 Persistence/Controller 对 Provider 文本格式产生依赖。
     * 空分类会被上游最低字段校验挡住，不会生成语义不明的空数组作为真实影片分类。</p>
     */
    private String genresJson(String genres) {
        return java.util.Arrays.stream(genres.split("[,，、/]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
