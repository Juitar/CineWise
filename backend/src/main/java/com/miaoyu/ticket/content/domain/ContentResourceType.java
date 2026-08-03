package com.miaoyu.ticket.content.domain;

/**
 * 内容模块支持的标准资源类型。
 *
 * <p>资源类型用于区分影片和影院的查询、缓存和快照，不能用场次或座位等票务类型扩展本枚举。</p>
 */
public enum ContentResourceType {
    /** 影片基础内容，由 D 管理。 */
    MOVIE,

    /** 影院基础内容，由 D 管理。 */
    CINEMA
}
