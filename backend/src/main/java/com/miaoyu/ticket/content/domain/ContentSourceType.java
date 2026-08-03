package com.miaoyu.ticket.content.domain;

/**
 * 内容数据来源的性质。
 *
 * <p>来源性质和来源名称分开保存，页面与推荐可以据此明确展示 Demo、实时或快照数据，
 * 不能把 Mock 数据描述为实时数据。</p>
 */
public enum ContentSourceType {
    /** 本地固定演示数据。 */
    MOCK,

    /** 已许可且实际调用成功的外部数据。 */
    LIVE,

    /** 从最近一次标准化内容记录中读取的数据。 */
    SNAPSHOT
}
