package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;
import java.util.Optional;

/**
 * 内容缓存端口。
 *
 * <p>Application 只依赖标准化结果，不接触 Redis 类型、序列化格式或键拼接；Redis 不可用时，
 * 调用方仍能继续尝试快照和 Demo 回退。</p>
 */
public interface ContentCachePort {

    /** 读取指定查询条件对应的标准化缓存内容，未命中不代表内容不存在。 */
    Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query);

    /** 保存标准化结果，缓存实现负责 TTL 和键格式，不能保存 Provider 原始响应。 */
    void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result);
}
