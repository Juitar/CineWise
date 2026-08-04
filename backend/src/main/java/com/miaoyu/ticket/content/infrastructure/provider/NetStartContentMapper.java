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
final class NetStartContentMapper {

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
        // 影片详情接口把字段放在 detailMovie；测试夹具可直接传该对象，避免原始结构扩散到业务层。
        JsonNode movie = raw.path("detailMovie").isObject() ? raw.path("detailMovie") : raw;
        return text(movie, "id").flatMap(id -> text(movie, "nm").flatMap(title -> text(movie, "cat")
                .flatMap(genres -> positiveInt(movie, "dur").flatMap(duration -> decimal(movie, "sc")
                        .map(rating -> new MovieContent(id, title, genres, duration, rating))))));
    }

    private Optional<ContentItem> mapCinema(JsonNode raw, String cityCode) {
        JsonNode info = raw.path("info").isObject() ? raw.path("info") : raw;
        return text(raw, "id").flatMap(id -> text(info, "name").flatMap(name -> text(info, "address")
                .flatMap(address -> text(cityCode).map(city -> new CinemaContent(id, name, city,
                        areaFromAddress(address), address, BigDecimal.ZERO, BigDecimal.ZERO)))));
    }

    private Optional<String> text(JsonNode node, String field) { return text(node.path(field).asText(null)); }
    private Optional<String> text(String value) {
        if (value == null || value.trim().isEmpty()) { return Optional.empty(); }
        return Optional.of(value.trim());
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
    private String areaFromAddress(String address) {
        int index = address.indexOf('区');
        return index > 0 ? address.substring(0, index + 1) : "未知区域";
    }
}
