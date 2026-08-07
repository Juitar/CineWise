package com.miaoyu.ticket.geo.application;

import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * B 已确认地点文本的统一解析入口。
 *
 * <p>该类不理解对话，只处理一个已经确认的文本。多个候选直接拒绝，交由 B 再向用户询问，避免静默
 * 选择错误城市、行政区或同名 POI。</p>
 */
@Component
public class GeocodingUserLocationAdapter implements UserLocationAdapter {

    private final PlaceGeocodingPort geocodingPort;

    public GeocodingUserLocationAdapter(PlaceGeocodingPort geocodingPort) {
        this.geocodingPort = geocodingPort;
    }

    /** 浏览器坐标由专用 Adapter 处理；这里保留接口完整性但不做地理编码。 */
    @Override
    public ResolvedGeoPoint fromBrowser(BigDecimal longitude, BigDecimal latitude) {
        return new ResolvedGeoPoint(longitude, latitude, com.miaoyu.ticket.geo.domain.LocationGranularity.DEVICE);
    }

    @Override
    public ResolvedGeoPoint fromPlaceText(String placeText) {
        if (placeText == null || placeText.isBlank()) {
            throw new IllegalArgumentException("placeText 不能为空");
        }
        List<PlaceGeocodingPort.Candidate> candidates = geocodingPort.geocode(placeText.trim());
        if (candidates == null || candidates.size() != 1) {
            throw new IllegalArgumentException("地点无法唯一确定");
        }
        PlaceGeocodingPort.Candidate candidate = candidates.getFirst();
        return new ResolvedGeoPoint(candidate.longitude(), candidate.latitude(), candidate.granularity());
    }
}
