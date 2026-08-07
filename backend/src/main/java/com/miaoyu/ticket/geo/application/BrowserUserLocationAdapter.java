package com.miaoyu.ticket.geo.application;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 浏览器坐标的统一校验实现。
 *
 * <p>浏览器坐标不应绕到地理编码服务：这样既避免把一次性位置发送给额外第三方，也避免把设备
 * 位置误降级为城市中心点。地点文本入口在高德编码 Adapter 配置就绪后由独立实现提供。</p>
 */
@Component
public class BrowserUserLocationAdapter {

    /** 将 C 已获授权的数值坐标直接标为设备位置。 */
    public ResolvedGeoPoint fromBrowser(BigDecimal longitude, BigDecimal latitude) {
        return new ResolvedGeoPoint(longitude, latitude, LocationGranularity.DEVICE);
    }
}
