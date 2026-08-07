package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;

/**
 * 用户主动发起的一次性基础路线请求。
 *
 * <p>起点只允许在本次 Provider 调用的内存中存在；本类型不应被日志、快照、缓存或异步任务保存，
 * 避免把精确坐标或路线信息变成长期数据。</p>
 */
public record BasicRouteCommand(ResolvedGeoPoint origin, String travelMode, boolean thirdPartySharingConfirmed) { }
