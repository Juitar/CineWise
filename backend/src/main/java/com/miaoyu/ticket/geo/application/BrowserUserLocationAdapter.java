package com.miaoyu.ticket.geo.application;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    /**
     * 浏览器可能返回超过 6 位小数；D 在入口按 HALF_UP 统一规范化，避免由各个前端页面自行截断。
     * 规范化后的坐标仍由 {@link ResolvedGeoPoint} 进行空值与范围校验。
     */
    public ResolvedGeoPoint fromBrowser(BigDecimal longitude, BigDecimal latitude) {
        return new ResolvedGeoPoint(normalize(longitude), normalize(latitude), LocationGranularity.DEVICE);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }
}
