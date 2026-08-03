package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 一次计划运行的不可变快照，计划定义和运行过程分别保存。 */
public record ExecutionRunState(ExecutionPlan plan, Map<String, ExecutionNodeState> nodeStates, int replanCount) {

    private static final int MAX_REPLAN_COUNT = 2;

    public ExecutionRunState {
        plan = Objects.requireNonNull(plan, "运行计划不能为空");
        nodeStates = copyAndValidateNodeStates(plan, nodeStates);
        if (replanCount < 0 || replanCount > MAX_REPLAN_COUNT) {
            throw new IllegalArgumentException("重规划次数必须在 0 到 " + MAX_REPLAN_COUNT + " 之间");
        }
    }

    /** 从已校验运行计划创建初始运行快照。 */
    public static ExecutionRunState initial(ExecutionPlan plan) {
        Objects.requireNonNull(plan, "运行计划不能为空");
        Map<String, ExecutionNodeState> initialStates = new LinkedHashMap<>();
        for (ExecutionPlanNode node : plan.nodes()) {
            initialStates.put(node.nodeId(), ExecutionNodeState.initial(node));
        }
        return new ExecutionRunState(plan, initialStates, 0);
    }

    /** 读取指定节点的运行状态。 */
    public ExecutionNodeState nodeState(String nodeId) {
        ExecutionNodeState state = nodeStates.get(nodeId);
        if (state == null) {
            throw new IllegalArgumentException("计划中不存在节点: " + nodeId);
        }
        return state;
    }

    /** 返回替换一个节点状态后的新快照。 */
    public ExecutionRunState withNodeState(ExecutionNodeState nextNodeState) {
        Objects.requireNonNull(nextNodeState, "节点状态不能为空");
        if (!nodeStates.containsKey(nextNodeState.nodeId())) {
            throw new IllegalArgumentException("计划中不存在节点: " + nextNodeState.nodeId());
        }
        Map<String, ExecutionNodeState> copiedStates = new LinkedHashMap<>(nodeStates);
        copiedStates.put(nextNodeState.nodeId(), nextNodeState);
        return new ExecutionRunState(plan, copiedStates, replanCount);
    }

    /** 返回重规划次数增加后的新快照。 */
    public ExecutionRunState withNextReplanCount() {
        return new ExecutionRunState(plan, nodeStates, replanCount + 1);
    }

    private static Map<String, ExecutionNodeState> copyAndValidateNodeStates(
            ExecutionPlan plan, Map<String, ExecutionNodeState> nodeStates) {
        Objects.requireNonNull(nodeStates, "节点状态不能为空");
        Map<String, ExecutionNodeState> copiedStates = new LinkedHashMap<>();
        for (Map.Entry<String, ExecutionNodeState> entry : nodeStates.entrySet()) {
            String nodeId = entry.getKey();
            ExecutionNodeState state = entry.getValue();
            if (nodeId == null || state == null || !nodeId.equals(state.nodeId())) {
                throw new IllegalArgumentException("节点状态键和值必须一致且非空");
            }
            copiedStates.put(nodeId, state);
        }
        Set<String> planNodeIds = plan.nodes().stream().map(ExecutionPlanNode::nodeId).collect(Collectors.toSet());
        if (!planNodeIds.equals(copiedStates.keySet())) {
            throw new IllegalArgumentException("节点状态必须与运行计划节点一一对应");
        }
        return Map.copyOf(copiedStates);
    }
}
