package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 路线 Provider 的 D 内部端口。
 *
 * <p>Provider 可接收一次性起点，但返回值只允许保留安全摘要；真实高德未配置时返回空，由应用层返回
 * 明确的 307001，不能编造文字路线。</p>
 */
@FunctionalInterface
public interface BasicRouteProvider {

    Optional<BasicRouteResult> plan(
            String originValue, String cinemaArea, String travelMode, OffsetDateTime requestedAt);
}
