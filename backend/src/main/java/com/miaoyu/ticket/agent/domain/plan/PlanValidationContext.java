package com.miaoyu.ticket.agent.domain.plan;

import java.util.Map;

/** 校验候选计划时由服务端提供的槽位、节点结果类型和脱敏快照。 */
public record PlanValidationContext(
        Map<String, Class<?>> slotTypes,
        Map<String, Class<?>> nodeResultTypes,
        SlotSnapshot slotSnapshot) {

    public PlanValidationContext {
        slotTypes = slotTypes == null ? Map.of() : Map.copyOf(slotTypes);
        nodeResultTypes = nodeResultTypes == null ? Map.of() : Map.copyOf(nodeResultTypes);
        slotSnapshot = slotSnapshot == null ? new SlotSnapshot(0L, Map.of()) : slotSnapshot;
    }
}
