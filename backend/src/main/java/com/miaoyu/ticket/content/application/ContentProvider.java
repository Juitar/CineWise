package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;
import java.util.Optional;

/**
 * 外部或 Demo 内容提供端口。
 *
 * <p>Provider 只返回标准化内容和来源封套，不泄露厂商原始字段；后续真实 Provider 替换 Demo
 * Provider 时，应用服务和推荐模块不需要改动。</p>
 */
public interface ContentProvider {

    /**
     * 按内容查询条件返回一批标准化影片或影院内容；没有匹配内容时使用空 Optional，
     * 由应用服务决定是否继续读取快照或 Demo，Provider 不得补造票务事实。
     */
    Optional<ContentResult<List<? extends ContentItem>>> query(ContentQuery query);
}
