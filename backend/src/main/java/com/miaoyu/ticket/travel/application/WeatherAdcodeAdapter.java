package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.util.Optional;

/** 将影院静态坐标转换为天气 Provider 使用的行政区码。 */
@FunctionalInterface
public interface WeatherAdcodeAdapter {
    /** 失败返回空，由天气服务使用明确标记的区域映射回退。 */
    Optional<String> resolve(ResolvedGeoPoint cinemaLocation);
}
