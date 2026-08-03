package com.miaoyu.ticket.agent.domain.plan;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 在任何工具调用前校验模型候选计划，并生成初始运行计划。 */
public final class PlanSchemaValidator {
    private static final int MAX_NODE_COUNT = 12;

    private final ToolRegistry toolRegistry;

    public PlanSchemaValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
    }

    /** 校验候选计划；失败时一次返回全部可确定的问题。 */
    public PlanValidationResult validate(CandidatePlan plan, PlanValidationContext context) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan == null) {
            issues.add(issue(PlanValidationIssueCode.PLAN_ID_REQUIRED, null, "plan", "候选计划不能为空"));
            return PlanValidationResult.invalid(issues);
        }
        PlanValidationContext validationContext = context == null
                ? new PlanValidationContext(Map.of(), Map.of(), new SlotSnapshot(0L, Map.of()))
                : context;
        validatePlanHeader(plan, issues);

        Map<String, CandidatePlanNode> nodesById = indexNodes(plan.nodes(), issues);
        validateNodeFields(plan.nodes(), issues);
        validateDependencies(nodesById, issues);
        validateCycles(nodesById, issues);
        validateToolNodes(nodesById, validationContext, issues);

        if (!issues.isEmpty()) {
            return PlanValidationResult.invalid(issues);
        }
        return PlanValidationResult.valid(toExecutionPlan(plan, validationContext.slotSnapshot()));
    }

    private static void validatePlanHeader(CandidatePlan plan, List<PlanValidationIssue> issues) {
        if (isBlank(plan.planId())) {
            issues.add(issue(PlanValidationIssueCode.PLAN_ID_REQUIRED, null, "planId", "planId 不能为空"));
        }
        if (plan.version() < 1) {
            issues.add(issue(PlanValidationIssueCode.PLAN_VERSION_INVALID, null, "version", "版本必须不小于 1"));
        }
        if (plan.nodes().size() > MAX_NODE_COUNT) {
            issues.add(issue(
                    PlanValidationIssueCode.NODE_LIMIT_EXCEEDED,
                    null,
                    "nodes",
                    "计划节点不能超过 " + MAX_NODE_COUNT));
        }
    }

    private static Map<String, CandidatePlanNode> indexNodes(
            List<CandidatePlanNode> nodes, List<PlanValidationIssue> issues) {
        Map<String, CandidatePlanNode> nodesById = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            CandidatePlanNode node = nodes.get(index);
            if (node == null) {
                issues.add(issue(PlanValidationIssueCode.NODE_REQUIRED, null, "nodes[" + index + "]", "节点不能为空"));
                continue;
            }
            if (isBlank(node.nodeId())) {
                issues.add(issue(
                        PlanValidationIssueCode.NODE_ID_REQUIRED,
                        null,
                        "nodes[" + index + "].nodeId",
                        "nodeId 不能为空"));
                continue;
            }
            if (nodesById.putIfAbsent(node.nodeId(), node) != null) {
                issues.add(issue(
                        PlanValidationIssueCode.NODE_ID_DUPLICATE,
                        node.nodeId(),
                        "nodeId",
                        "nodeId 不能重复"));
            }
        }
        return nodesById;
    }

    private static void validateNodeFields(List<CandidatePlanNode> nodes, List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.type() == null) {
                issues.add(issue(PlanValidationIssueCode.NODE_TYPE_REQUIRED, node.nodeId(), "type", "节点类型不能为空"));
            }
            if (node.failurePolicy() == null) {
                issues.add(issue(
                        PlanValidationIssueCode.NODE_FIELD_REQUIRED,
                        node.nodeId(),
                        "failurePolicy",
                        "失败策略不能为空"));
            }
            if (node.type() == PlanNodeType.CALL_TOOL && isBlank(node.targetName())) {
                issues.add(issue(
                        PlanValidationIssueCode.NODE_FIELD_REQUIRED,
                        node.nodeId(),
                        "targetName",
                        "工具节点必须提供 targetName"));
            }
        }
    }

    private static void validateDependencies(
            Map<String, CandidatePlanNode> nodesById, List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodesById.values()) {
            for (String dependencyId : node.dependsOn()) {
                if (isBlank(dependencyId) || !nodesById.containsKey(dependencyId)) {
                    issues.add(issue(
                            PlanValidationIssueCode.DEPENDENCY_INVALID,
                            node.nodeId(),
                            "dependsOn",
                            "依赖节点不存在"));
                }
            }
        }
    }

    private static void validateCycles(Map<String, CandidatePlanNode> nodesById, List<PlanValidationIssue> issues) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodesById.keySet()) {
            detectCycle(nodeId, nodesById, visited, visiting, issues);
        }
    }

    private static void detectCycle(
            String nodeId,
            Map<String, CandidatePlanNode> nodesById,
            Set<String> visited,
            Set<String> visiting,
            List<PlanValidationIssue> issues) {
        boolean cycleAlreadyReported = issues.stream()
                .anyMatch(issue -> issue.code() == PlanValidationIssueCode.DEPENDENCY_CYCLE);
        if (visited.contains(nodeId) || cycleAlreadyReported) {
            return;
        }
        if (!visiting.add(nodeId)) {
            issues.add(issue(PlanValidationIssueCode.DEPENDENCY_CYCLE, nodeId, "dependsOn", "依赖图不能有环"));
            return;
        }
        for (String dependencyId : nodesById.get(nodeId).dependsOn()) {
            if (nodesById.containsKey(dependencyId)) {
                detectCycle(dependencyId, nodesById, visited, visiting, issues);
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private void validateToolNodes(
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodesById.values()) {
            if (node.type() != PlanNodeType.CALL_TOOL || isBlank(node.targetName())) {
                continue;
            }
            ToolDefinition definition = toolRegistry.find(node.targetName()).orElse(null);
            if (definition == null) {
                issues.add(issue(PlanValidationIssueCode.TOOL_NOT_FOUND, node.nodeId(), "targetName", "工具未注册"));
                continue;
            }
            validateToolInputs(node, definition, nodesById, context, issues);
            if (!definition.readOnly()) {
                issues.add(issue(
                        PlanValidationIssueCode.WRITE_TOOL_NOT_SUPPORTED,
                        node.nodeId(),
                        "targetName",
                        "当前基础计划不支持执行写工具"));
            }
        }
    }

    private void validateToolInputs(
            CandidatePlanNode node,
            ToolDefinition definition,
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        Map<String, ToolInputDefinition> inputDefinitions = new HashMap<>();
        for (ToolInputDefinition inputDefinition : definition.inputs()) {
            inputDefinitions.put(inputDefinition.name(), inputDefinition);
        }
        Set<String> referencedInputs = new HashSet<>();
        for (InputReference inputReference : node.inputRefs()) {
            if (inputReference == null || isBlank(inputReference.inputName()) || inputReference.source() == null
                    || isBlank(inputReference.sourceId())) {
                issues.add(issue(
                        PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                        node.nodeId(),
                        "inputRefs",
                        "输入引用不完整"));
                continue;
            }
            ToolInputDefinition inputDefinition = inputDefinitions.get(inputReference.inputName());
            if (inputDefinition == null) {
                issues.add(issue(
                        PlanValidationIssueCode.TOOL_INPUT_UNKNOWN,
                        node.nodeId(),
                        "inputRefs." + inputReference.inputName(),
                        "工具未声明该输入字段"));
                continue;
            }
            referencedInputs.add(inputReference.inputName());
            Class<?> sourceType = resolveInputSourceType(inputReference, node, nodesById, context, issues);
            if (sourceType != null && !inputDefinition.valueType().isAssignableFrom(sourceType)) {
                issues.add(issue(
                        PlanValidationIssueCode.INPUT_TYPE_MISMATCH,
                        node.nodeId(),
                        "inputRefs." + inputReference.inputName(),
                        "输入引用类型与工具字段类型不匹配"));
            }
        }
        for (ToolInputDefinition requiredInput : definition.requiredInputs()) {
            if (!referencedInputs.contains(requiredInput.name())) {
                issues.add(issue(
                        PlanValidationIssueCode.TOOL_REQUIRED_INPUT_MISSING,
                        node.nodeId(),
                        "inputRefs." + requiredInput.name(),
                        "缺少工具必填输入"));
            }
        }
    }

    private Class<?> resolveInputSourceType(
            InputReference inputReference,
            CandidatePlanNode node,
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        if (inputReference.source() == InputReferenceSource.SLOT) {
            Class<?> slotType = context.slotTypes().get(inputReference.sourceId());
            if (slotType == null) {
                issues.add(issue(
                        PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                        node.nodeId(),
                        "inputRefs." + inputReference.inputName(),
                        "引用的槽位不存在"));
            }
            return slotType;
        }
        CandidatePlanNode sourceNode = nodesById.get(inputReference.sourceId());
        if (sourceNode == null) {
            issues.add(issue(
                    PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                    node.nodeId(),
                    "inputRefs." + inputReference.inputName(),
                    "引用的上游节点不存在"));
            return null;
        }
        if (!isUpstream(inputReference.sourceId(), node.nodeId(), nodesById)) {
            issues.add(issue(
                    PlanValidationIssueCode.INPUT_REFERENCE_NOT_UPSTREAM,
                    node.nodeId(),
                    "inputRefs." + inputReference.inputName(),
                    "输入只能引用上游节点结果"));
            return null;
        }
        if (sourceNode.type() == PlanNodeType.CALL_TOOL && !isBlank(sourceNode.targetName())) {
            ToolDefinition sourceDefinition = toolRegistry.find(sourceNode.targetName()).orElse(null);
            if (sourceDefinition != null) {
                return sourceDefinition.resultType();
            }
        }
        Class<?> resultType = context.nodeResultTypes().get(inputReference.sourceId());
        if (resultType == null) {
            issues.add(issue(
                    PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                    node.nodeId(),
                    "inputRefs." + inputReference.inputName(),
                    "上游节点结果类型未声明"));
        }
        return resultType;
    }

    private static boolean isUpstream(
            String sourceNodeId, String targetNodeId, Map<String, CandidatePlanNode> nodesById) {
        Deque<String> pendingNodeIds = new ArrayDeque<>(nodesById.get(targetNodeId).dependsOn());
        Set<String> visited = new HashSet<>();
        while (!pendingNodeIds.isEmpty()) {
            String currentNodeId = pendingNodeIds.removeFirst();
            if (!visited.add(currentNodeId)) {
                continue;
            }
            if (sourceNodeId.equals(currentNodeId)) {
                return true;
            }
            CandidatePlanNode currentNode = nodesById.get(currentNodeId);
            if (currentNode != null) {
                pendingNodeIds.addAll(currentNode.dependsOn());
            }
        }
        return false;
    }

    private static ExecutionPlan toExecutionPlan(CandidatePlan plan, SlotSnapshot slotSnapshot) {
        List<ExecutionPlanNode> nodes = plan.nodes().stream()
                .map(node -> new ExecutionPlanNode(
                        node.nodeId(),
                        node.type(),
                        node.targetName(),
                        node.inputRefs(),
                        node.dependsOn(),
                        node.failurePolicy(),
                        PlanNodeStatus.PENDING,
                        node.type() == PlanNodeType.CONFIRM_ACTION,
                        false,
                        null,
                        null,
                        slotSnapshot))
                .toList();
        return new ExecutionPlan(plan.planId(), plan.version(), nodes);
    }

    private static PlanValidationIssue issue(
            PlanValidationIssueCode code, String nodeId, String fieldPath, String message) {
        return new PlanValidationIssue(code, nodeId, fieldPath, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
