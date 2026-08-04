package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;

/**
 * 候选真实 Provider 向同步用例提供已标准化的内容，不暴露 HTTP 或 JSON。
 *
 * <p>该端口只给定时同步使用，页面查询只能读取缓存、快照或 Demo。这样用户请求不会因网络超时、
 * 第三方限流或字段变化而直接失败，也不会把外部 Provider 变成页面的隐式依赖。</p>
 */
public interface LiveContentSyncPort {
    /** Provider 关闭、非学习环境或本轮没有合格数据时返回空列表，调用方不得以此清空旧快照。 */
    List<SynchronizedContent> fetchForDailySync();

    /** 单项同时携带规范化查询键和 LIVE 内容封套，以便只替换对应的真实快照与缓存。 */
    record SynchronizedContent(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
}
