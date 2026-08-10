package com.miaoyu.ticket.agent.application.location;

import java.math.BigDecimal;
import java.util.Optional;

/** 仅在当前 HTTP 请求内将浏览器位置转换为城市名称，不持久化任何坐标。 */
public interface BrowserCityResolver {

    Optional<String> resolveCity(BigDecimal longitude, BigDecimal latitude);
}
