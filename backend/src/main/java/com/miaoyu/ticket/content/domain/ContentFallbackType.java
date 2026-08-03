package com.miaoyu.ticket.content.domain;

/**
 * 查询未直接取得实时数据时采用的回退层级。
 *
 * <p>该枚举只说明结果从哪一层取得；它不改变影片、影院内容本身，也不能被用来伪造场次或库存。</p>
 */
public enum ContentFallbackType {
    /** 命中未过期的标准化缓存。 */
    CACHE,

    /** 读取最近一次标准化持久化快照。 */
    SNAPSHOT,

    /** 最后回退到版本化本地演示数据。 */
    MOCK
}
