package com.miaoyu.ticket.travel.application;

/**
 * 用户主动发起的一次性基础路线请求。
 *
 * <p>originValue 只允许在本次 Provider 调用的内存中存在；本类型不应被日志、快照、缓存或异步任务
 * 保存，避免把精确坐标、手动地点或路线信息变成长期数据。</p>
 */
public record BasicRouteCommand(
        OriginType originType, String originValue, String travelMode, boolean thirdPartySharingConfirmed) {

    public enum OriginType { DEVICE, MANUAL }
}
