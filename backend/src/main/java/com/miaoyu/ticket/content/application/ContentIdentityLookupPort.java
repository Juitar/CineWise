package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.Optional;
import java.util.Map;

/**
 * 按本库实际内容主键查回固定 Demo 的来源 ID。
 *
 * <p>Demo JSON 不保存环境相关的雪花 ID。按 movieId 或 cinemaId 回退时，Provider 必须先经过该端口
 * 验证该主键确实对应共享目录中的来源 ID，不能把一次精确查询降级成整份目录。</p>
 */
public interface ContentIdentityLookupPort {

    /** 未找到、已删除或不是 Demo 来源的记录返回空，调用方不得返回无关 Demo 内容。 */
    Optional<String> findSourceId(ContentResourceType resourceType, long contentId);

    /** 列表展示把 Demo 来源身份映射为当前库实际业务 ID，不能向前端返回空 ID。 */
    default Map<String, Long> findContentIds(ContentResourceType resourceType, java.util.Set<String> sourceIds) {
        return Map.of();
    }
}
