package com.miaoyu.ticket.agent.domain.plan;

import java.util.Map;

/** 服务端生成运行计划时保存的脱敏槽位快照。 */
public record SlotSnapshot(long version, Map<String, String> values) {

    public SlotSnapshot {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
