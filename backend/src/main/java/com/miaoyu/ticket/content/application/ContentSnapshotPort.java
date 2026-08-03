package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;
import java.util.Optional;

/**
 * 内容快照端口。
 *
 * <p>快照是外部内容不可用时的可追溯回退来源；Application 不接触 Mapper 或表字段，
 * 由基础设施实现负责 `external_data_snapshot` 的唯一键和审计字段。</p>
 */
public interface ContentSnapshotPort {

    /** 读取指定查询条件最近一次标准化快照，是否允许使用过期快照由查询用例决定。 */
    Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query);

    /** 保存标准化快照；具体唯一键和审计字段由持久化适配器处理。 */
    void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result);
}
