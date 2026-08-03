package com.miaoyu.ticket.content.domain;

/** 影片和影院标准化内容的共同标记，不包含场次、价格、座位或库存。 */
public sealed interface ContentItem permits MovieContent, CinemaContent {

    /** 返回对应资源类型，供查询端口区分影片和影院。 */
    ContentResourceType resourceType();
}
