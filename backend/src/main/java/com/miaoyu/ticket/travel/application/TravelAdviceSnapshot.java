package com.miaoyu.ticket.travel.application;

import java.time.LocalDateTime;

/**
 * 一次生成后的不可变出行建议快照。
 *
 * <p>快照只保存影院区域天气和通用交通提示，不包含用户起点、路线折线或途经点。旧快照不能被刷新
 * 操作覆盖，方便用户在天气数据过期后仍能区分当时生成的内容和当前事实。</p>
 */
public record TravelAdviceSnapshot(
        long id,
        long taskId,
        long taskVersion,
        String weatherJson,
        String adviceJson,
        String source,
        LocalDateTime dataTime,
        LocalDateTime expiresAt,
        boolean isExpired,
        boolean degraded,
        String fallbackType,
        LocalDateTime createdAt) {
}
