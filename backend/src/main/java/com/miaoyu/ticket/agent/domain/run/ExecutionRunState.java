package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一次计划运行的不可变快照，计划定义和运行过程分别保存。
 *
 * <p>它是纯内存值对象：不等同于会话、数据库记录或 SSE 游标。未来持久化运行时必须以稳定运行 ID 和
 * 条件更新保护并发，不能把本对象的不可变形式误当成跨请求并发控制。
 */
public final class ExecutionRunState {
    /** 必须与状态机的重规划上限一致，避免快照接受状态机永远不会产生的次数。 */
    private static final int MAX_REPLAN_COUNT = 2;

    private final ExecutionPlan plan;
    private final Map<String, ExecutionNodeState> nodeStates;
    private final int replanCount;

    private ExecutionRunState(ExecutionPlan plan, Map<String, ExecutionNodeState> nodeStates, int replanCount) {
        this.plan = Objects.requireNonNull(plan, "运行计划不能为空");
        this.nodeStates = copyAndValidateNodeStates(plan, nodeStates);
        if (replanCount < 0 || replanCount > MAX_REPLAN_COUNT) {
            // 计数在构造时校验，防止外层复制快照时绕过状态机的有限重规划规则。
            throw new IllegalArgumentException("重规划次数必须在 0 到 " + MAX_REPLAN_COUNT + " 之间");
        }
        this.replanCount = replanCount;
    }

    /** 返回已校验的计划定义；调用方不得据此修改节点列表或跳过计划校验。 */
    public ExecutionPlan plan() {
        return plan;
    }

    /** 返回不可修改的节点状态快照；状态改变必须经 withNodeState 创建新快照。 */
    public Map<String, ExecutionNodeState> nodeStates() {
        return nodeStates;
    }

    /** 返回已批准的重规划次数；该数不表示已经调用模型的次数。 */
    public int replanCount() {
        return replanCount;
    }

    /** 读取指定节点的运行状态；未知节点必须失败，不能用默认 PENDING 状态掩盖计划错误。 */
    public ExecutionNodeState nodeState(String nodeId) {
        ExecutionNodeState state = nodeStates.get(nodeId);
        if (state == null) {
            throw new IllegalArgumentException("计划中不存在节点: " + nodeId);
        }
        return state;
    }

    static ExecutionRunState initial(ExecutionPlan plan) {
        // 每个计划节点都必须有一份独立初始状态，不能只给可执行工具节点建状态。
        Objects.requireNonNull(plan, "运行计划不能为空");
        Map<String, ExecutionNodeState> initialStates = new LinkedHashMap<>();
        for (ExecutionPlanNode node : plan.nodes()) {
            // LinkedHashMap 保留计划顺序，方便测试和将来安全地展示结构化运行轨迹。
            initialStates.put(node.nodeId(), ExecutionNodeState.initial(node));
        }
        return new ExecutionRunState(plan, initialStates, 0);
    }

    ExecutionRunState withNodeState(ExecutionNodeState nextNodeState) {
        Objects.requireNonNull(nextNodeState, "节点状态不能为空");
        if (!nodeStates.containsKey(nextNodeState.nodeId())) {
            // 旧计划或伪造节点不能被插入当前快照，避免状态集合与计划定义失去对应关系。
            throw new IllegalArgumentException("计划中不存在节点: " + nextNodeState.nodeId());
        }
        // 复制后替换单个节点，调用方持有的旧快照仍可用于审计前一状态。
        Map<String, ExecutionNodeState> copiedStates = new LinkedHashMap<>(nodeStates);
        copiedStates.put(nextNodeState.nodeId(), nextNodeState);
        return new ExecutionRunState(plan, copiedStates, replanCount);
    }

    ExecutionRunState withNextReplanCount() {
        // 上限由构造器再次校验；只有状态机在确认还有额度时才会调用本方法。
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
                // Map 键与对象 nodeId 不一致会使按节点查找得到错误状态，必须拒绝而不是自动修正。
                throw new IllegalArgumentException("节点状态键和值必须一致且非空");
            }
            copiedStates.put(nodeId, state);
        }
        Set<String> planNodeIds = plan.nodes().stream().map(ExecutionPlanNode::nodeId).collect(Collectors.toSet());
        if (!planNodeIds.equals(copiedStates.keySet())) {
            // 节点状态必须既不缺也不多，确保依赖解析和失败跳过覆盖完整计划。
            throw new IllegalArgumentException("节点状态必须与运行计划节点一一对应");
        }
        return Map.copyOf(copiedStates);
    }
}
