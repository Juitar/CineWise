package com.miaoyu.ticket.geo.application;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import java.math.BigDecimal;
import java.util.List;

/** 高德等地理编码 Provider 的最小输入输出边界。 */
@FunctionalInterface
public interface PlaceGeocodingPort {

    /** 返回所有合法候选；调用方只能在唯一候选时继续。 */
    List<Candidate> geocode(String placeText);

    /** Provider 原始字段在 Adapter 内映射，不泄露 adcode、POI ID 或原始 JSON。 */
    record Candidate(BigDecimal longitude, BigDecimal latitude, LocationGranularity granularity) { }
}
