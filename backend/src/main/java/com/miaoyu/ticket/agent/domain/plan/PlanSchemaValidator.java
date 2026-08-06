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

/**
 * 在任何工具调用前校验模型候选计划，并生成初始运行计划。
 *
 * <p>候选计划来自模型，必须按不可信输入处理。通过本校验器只表示它符合 B 已登记的计划结构和工具
 * 白名单，不表示工具查询结果真实、更不表示用户已经确认写操作。
 *
 * <p>校验与执行刻意分离：本类不调用工具、不改变运行状态，也不保存会话。这样即使模型构造了循环、
 * 未登记工具或伪造的上游输出，也会在产生任何下游副作用之前被拒绝。
 */
public final class PlanSchemaValidator {
    /**
     * 单个计划的硬上限。
     *
     * <p>该限制控制一次请求的校验和状态推进成本，不能由模型或客户端放大；真实复杂流程应拆成后续
     * 受控请求，而不是在一份候选计划里堆积节点。
     */
    private static final int MAX_NODE_COUNT = 12;

    private final ToolRegistry toolRegistry;

    public PlanSchemaValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
    }

    /**
     * 校验候选计划；失败时一次返回全部可确定的问题。
     *
     * <p>返回全部静态可确定的问题可以减少模型或调用方反复试探校验器，但不会暴露工具实现、Bean 名称
     * 或内部异常。调用方只应使用稳定问题码和 fieldPath 做安全回复或审计。
     *
     * <p>空 context 使用无槽位、无上游类型的安全默认值。它不会默认为任何工具输入已存在，因此空上下文
     * 无法绕过必填槽位检查。
     */
    public PlanValidationResult validate(CandidatePlan plan, PlanValidationContext context) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan == null) {
            // 空计划没有节点可继续检查，立即返回可识别的问题码而不是抛出空指针异常。
            issues.add(issue(PlanValidationIssueCode.PLAN_ID_REQUIRED, null, "plan", "候选计划不能为空"));
            return PlanValidationResult.invalid(issues);
        }
        PlanValidationContext validationContext = context == null
                ? new PlanValidationContext(Map.of(), Map.of(), new SlotSnapshot(0L, Map.of()))
                : context;
        // 先检查头部和节点索引，再做依赖、工具与类型校验，避免一个格式错误引发无意义的后续异常。
        validatePlanHeader(plan, issues);

        Map<String, CandidatePlanNode> nodesById = indexNodes(plan.nodes(), issues);
        validateNodeFields(plan.nodes(), issues);
        validateDependencies(nodesById, issues);
        validateCycles(nodesById, issues);
        // 工具校验最后执行；只有服务端注册表能决定工具名、输入与返回类型是否允许。
        validateToolNodes(nodesById, validationContext, issues);

        if (!issues.isEmpty()) {
            return PlanValidationResult.invalid(issues);
        }
        // 执行计划保存当前槽位快照，后续适配器只能从该快照取值，不能再读取模型原始文本。
        return PlanValidationResult.valid(toExecutionPlan(plan, validationContext.slotSnapshot()));
    }

    /**
     * 校验计划本身的基础边界。
     *
     * <p>planId 用于关联运行快照，version 用于调用方识别计划格式，节点数限制用于防止模型把一次请求
     * 扩展成不可控的循环工作量。这里不把缺失字段修成默认值，默认值会掩盖模型输出问题。
     */
    private static void validatePlanHeader(CandidatePlan plan, List<PlanValidationIssue> issues) {
        if (isBlank(plan.planId())) {
            issues.add(issue(PlanValidationIssueCode.PLAN_ID_REQUIRED, null, "planId", "planId 不能为空"));
        }
        if (plan.version() < 1) {
            issues.add(issue(PlanValidationIssueCode.PLAN_VERSION_INVALID, null, "version", "版本必须不小于 1"));
        }
        if (plan.nodes().size() > MAX_NODE_COUNT) {
            // 节点数超限仍继续收集其余格式问题，但最终不会被转换为可执行计划。
            issues.add(issue(
                    PlanValidationIssueCode.NODE_LIMIT_EXCEEDED,
                    null,
                    "nodes",
                    "计划节点不能超过 " + MAX_NODE_COUNT));
        }
    }

    /**
     * 建立按 nodeId 查询的索引，同时检查空节点、空标识和重复标识。
     *
     * <p>重复 nodeId 不能“后者覆盖前者”。覆盖会让依赖关系在校验和执行阶段指向不同节点，导致工具
     * 输入来源不确定，因此只保留首个节点继续收集问题。
     */
    private static Map<String, CandidatePlanNode> indexNodes(
            List<CandidatePlanNode> nodes, List<PlanValidationIssue> issues) {
        Map<String, CandidatePlanNode> nodesById = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            CandidatePlanNode node = nodes.get(index);
            if (node == null) {
                // 保留数组下标，方便调用方定位模型返回的错误项而不泄露整份原始计划。
                issues.add(issue(PlanValidationIssueCode.NODE_REQUIRED, null, "nodes[" + index + "]", "节点不能为空"));
                continue;
            }
            if (isBlank(node.nodeId())) {
                // 没有 nodeId 的节点不能参与依赖图，也不能安全映射到运行状态。
                issues.add(issue(
                        PlanValidationIssueCode.NODE_ID_REQUIRED,
                        null,
                        "nodes[" + index + "].nodeId",
                        "nodeId 不能为空"));
                continue;
            }
            if (nodesById.putIfAbsent(node.nodeId(), node) != null) {
                // 不覆盖原索引，保证后续校验始终使用稳定的节点定义。
                issues.add(issue(
                        PlanValidationIssueCode.NODE_ID_DUPLICATE,
                        node.nodeId(),
                        "nodeId",
                        "nodeId 不能重复"));
            }
        }
        return nodesById;
    }

    /**
     * 检查每个节点在不考虑依赖关系时必须具备的字段。
     *
     * <p>只有 CALL_TOOL 需要 targetName；其他节点不在此处要求目标字段，以免把未来明确设计的非工具
     * 节点错误地限制成工具调用。
     */
    private static void validateNodeFields(List<CandidatePlanNode> nodes, List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodes) {
            if (node == null) {
                // 空节点已由索引阶段报告，不重复添加同一条问题。
                continue;
            }
            if (node.type() == null) {
                // 没有类型就无法决定后续字段与状态规则，仍继续检查其他独立字段。
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

    /**
     * 校验每个 dependsOn 都指向本计划内可识别的节点。
     *
     * <p>不存在的依赖不能被当成已经完成；那会让下游节点意外提前可运行，尤其可能导致工具输入引用
     * 没有可靠上游来源。
     */
    private static void validateDependencies(
            Map<String, CandidatePlanNode> nodesById, List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodesById.values()) {
            for (String dependencyId : node.dependsOn()) {
                if (isBlank(dependencyId) || !nodesById.containsKey(dependencyId)) {
                    // 空依赖与未知依赖的运行后果相同：都不能形成可验证的有向无环图。
                    issues.add(issue(
                            PlanValidationIssueCode.DEPENDENCY_INVALID,
                            node.nodeId(),
                            "dependsOn",
                            "依赖节点不存在"));
                }
            }
        }
    }

    /**
     * 检查依赖图没有环。
     *
     * <p>状态机只支持从 PENDING 向终态推进，不能处理 A 依赖 B、B 又依赖 A 的等待关系。发现首个环后
     * 停止继续报环，避免同一环被多个起点重复放大为大量问题。
     */
    private static void validateCycles(Map<String, CandidatePlanNode> nodesById, List<PlanValidationIssue> issues) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodesById.keySet()) {
            detectCycle(nodeId, nodesById, visited, visiting, issues);
        }
    }

    /**
     * 使用 DFS 的 visiting 集合识别回边。
     *
     * <p>visited 代表节点及其依赖已经完整检查过，visiting 只代表当前递归路径。二者不能合并，否则
     * 会把共享上游误判成环，或漏掉真正的回边。
     */
    private static void detectCycle(
            String nodeId,
            Map<String, CandidatePlanNode> nodesById,
            Set<String> visited,
            Set<String> visiting,
            List<PlanValidationIssue> issues) {
        boolean cycleAlreadyReported = issues.stream()
                .anyMatch(issue -> issue.code() == PlanValidationIssueCode.DEPENDENCY_CYCLE);
        if (visited.contains(nodeId) || cycleAlreadyReported) {
            // 已确认无环的子图无需重复遍历；一个计划只需要一个稳定的环问题码。
            return;
        }
        if (!visiting.add(nodeId)) {
            // 同一路径再次遇到节点才是环，不把不同分支引用同一上游视为错误。
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

    /**
     * 校验工具节点只能引用服务端登记的只读工具。
     *
     * <p>工具注册表是唯一白名单来源。计划里的 targetName 只是待核对字符串，不能据此反射加载类、
     * 获取 Spring Bean 或访问其他模块的 Controller、Repository。
     */
    private void validateToolNodes(
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        for (CandidatePlanNode node : nodesById.values()) {
            if (node.type() != PlanNodeType.CALL_TOOL || isBlank(node.targetName())) {
                // 非工具节点由各自的主控语义处理，不在这里强行套用工具输入规则。
                continue;
            }
            ToolDefinition definition = toolRegistry.find(node.targetName()).orElse(null);
            if (definition == null) {
                // 未登记目标在工具执行前拒绝，不能用“暂时无实现”作为可跳过节点。
                issues.add(issue(PlanValidationIssueCode.TOOL_NOT_FOUND, node.nodeId(), "targetName", "工具未注册"));
                continue;
            }
            validateToolInputs(node, definition, nodesById, context, issues);
            if (!definition.readOnly() && !hasConfirmationDependency(node, nodesById, new HashSet<>())) {
                // 写工具只允许在一个已校验确认节点之后等待；计划本身不能附带 actionId 或写键。
                issues.add(issue(
                        PlanValidationIssueCode.WRITE_TOOL_CONFIRMATION_REQUIRED,
                        node.nodeId(),
                        "dependsOn",
                        "写工具必须依赖确认节点"));
            }
        }
    }

    private static boolean hasConfirmationDependency(
            CandidatePlanNode node, Map<String, CandidatePlanNode> nodesById, Set<String> visited) {
        for (String dependencyId : node.dependsOn()) {
            if (!visited.add(dependencyId)) {
                continue;
            }
            CandidatePlanNode dependency = nodesById.get(dependencyId);
            if (dependency != null && (dependency.type() == PlanNodeType.CONFIRM_ACTION
                    || hasConfirmationDependency(dependency, nodesById, visited))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验工具输入名称、引用完整性、来源类型和必填项。
     *
     * <p>输入名称必须与 ToolDefinition 完全一致，避免模型通过增加相似字段影响适配器。类型比较在计划
     * 阶段完成，实际值仍由工具适配器在执行前解析并校验格式。
     */
    private void validateToolInputs(
            CandidatePlanNode node,
            ToolDefinition definition,
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        Map<String, ToolInputDefinition> inputDefinitions = new HashMap<>();
        for (ToolInputDefinition inputDefinition : definition.inputs()) {
            // ToolDefinition 已在注册时校验唯一性，这里建立索引只用于按引用名称快速核对。
            inputDefinitions.put(inputDefinition.name(), inputDefinition);
        }
        Set<String> referencedInputs = new HashSet<>();
        for (InputReference inputReference : node.inputRefs()) {
            if (inputReference == null || isBlank(inputReference.inputName()) || inputReference.source() == null
                    || isBlank(inputReference.sourceId())) {
                // 不完整引用没有可信来源，不能使用 null、空串或模型猜测值补齐。
                issues.add(issue(
                        PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                        node.nodeId(),
                        "inputRefs",
                        "输入引用不完整"));
                continue;
            }
            ToolInputDefinition inputDefinition = inputDefinitions.get(inputReference.inputName());
            if (inputDefinition == null) {
                // 未声明字段不能传给 D 的 Command，避免 B 在无契约情况下扩大跨模块接口。
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
                // 只接受可赋值类型，禁止把字符串标识误当作日期、金额或任意对象传入工具。
                issues.add(issue(
                        PlanValidationIssueCode.INPUT_TYPE_MISMATCH,
                        node.nodeId(),
                        "inputRefs." + inputReference.inputName(),
                        "输入引用类型与工具字段类型不匹配"));
            }
        }
        for (ToolInputDefinition requiredInput : definition.requiredInputs()) {
            if (!referencedInputs.contains(requiredInput.name())) {
                // 缺少引用与引用空值不同：前者属于计划结构问题，后者会在执行时归为参数不合法。
                issues.add(issue(
                        PlanValidationIssueCode.TOOL_REQUIRED_INPUT_MISSING,
                        node.nodeId(),
                        "inputRefs." + requiredInput.name(),
                        "缺少工具必填输入"));
            }
        }
    }

    /**
     * 解析输入来源的声明类型，并验证节点结果只能来自真正上游。
     *
     * <p>SLOT 类型来自服务端 validationContext；NODE_RESULT 类型来自已登记工具定义或服务端声明，
     * 而不是模型声称的结果格式。这样模型不能伪造上游对象来满足下游工具参数。
     */
    private Class<?> resolveInputSourceType(
            InputReference inputReference,
            CandidatePlanNode node,
            Map<String, CandidatePlanNode> nodesById,
            PlanValidationContext context,
            List<PlanValidationIssue> issues) {
        if (inputReference.source() == InputReferenceSource.SLOT) {
            Class<?> slotType = context.slotTypes().get(inputReference.sourceId());
            if (slotType == null) {
                // 槽位不存在时不尝试从用户原始输入提取，追问和填槽必须由外层受控流程完成。
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
            // 节点来源不存在时不能退化为任意常量或空对象。
            issues.add(issue(
                    PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                    node.nodeId(),
                    "inputRefs." + inputReference.inputName(),
                    "引用的上游节点不存在"));
            return null;
        }
        if (!isUpstream(inputReference.sourceId(), node.nodeId(), nodesById)) {
            // 同计划但非上游的结果可能尚未生成，引用它会制造执行顺序和数据竞争问题。
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
                // 已登记工具的结果类型优先于模型声明，保证跨工具类型由注册定义唯一决定。
                return sourceDefinition.resultType();
            }
        }
        Class<?> resultType = context.nodeResultTypes().get(inputReference.sourceId());
        if (resultType == null) {
            // 非工具节点的结果类型必须由服务端上下文显式登记，不能依据节点名称推断。
            issues.add(issue(
                    PlanValidationIssueCode.INPUT_REFERENCE_INVALID,
                    node.nodeId(),
                    "inputRefs." + inputReference.inputName(),
                    "上游节点结果类型未声明"));
        }
        return resultType;
    }

    /**
     * 从目标节点向上遍历依赖，判断来源节点是否真正处于其上游。
     *
     * <p>使用显式队列而不是递归，避免合法但较深的计划图造成调用栈风险；visited 防止异常图中的重复
     * 遍历。真正的环已由前置校验单独报告。
     */
    private static boolean isUpstream(
            String sourceNodeId, String targetNodeId, Map<String, CandidatePlanNode> nodesById) {
        Deque<String> pendingNodeIds = new ArrayDeque<>(nodesById.get(targetNodeId).dependsOn());
        Set<String> visited = new HashSet<>();
        while (!pendingNodeIds.isEmpty()) {
            String currentNodeId = pendingNodeIds.removeFirst();
            if (!visited.add(currentNodeId)) {
                // 多个依赖分支汇合时只检查一次，不改变可达性判断。
                continue;
            }
            if (sourceNodeId.equals(currentNodeId)) {
                // 找到任意依赖路径即可证明来源先于目标，不要求它是直接依赖。
                return true;
            }
            CandidatePlanNode currentNode = nodesById.get(currentNodeId);
            if (currentNode != null) {
                pendingNodeIds.addAll(currentNode.dependsOn());
            }
        }
        return false;
    }

    /**
     * 将已校验候选复制为初始执行计划。
     *
     * <p>所有节点从 PENDING 开始，确认节点只标记“需要确认”而不创建 actionId。确认动作、持久化和
     * 写工具执行属于后续能力，不能由计划转换阶段擅自补上。
     */
    private ExecutionPlan toExecutionPlan(CandidatePlan plan, SlotSnapshot slotSnapshot) {
        List<ExecutionPlanNode> nodes = plan.nodes().stream()
                .map(node -> new ExecutionPlanNode(
                        node.nodeId(),
                        node.type(),
                        node.targetName(),
                        node.inputRefs(),
                        node.dependsOn(),
                        node.failurePolicy(),
                        PlanNodeStatus.PENDING,
                        node.type() == PlanNodeType.CONFIRM_ACTION
                                || (node.type() == PlanNodeType.CALL_TOOL
                                && toolRegistry.find(node.targetName())
                                        .map(definition -> !definition.readOnly())
                                        .orElse(false)),
                        false,
                        null,
                        null,
                        // 每个节点持有同一版本的快照，工具适配器据此记录实际引用的服务端槽位版本。
                        slotSnapshot))
                .toList();
        return new ExecutionPlan(plan.planId(), plan.version(), nodes);
    }

    private static PlanValidationIssue issue(
            PlanValidationIssueCode code, String nodeId, String fieldPath, String message) {
        // 统一构造保证所有拒绝路径都有稳定问题码，可供测试和安全回复使用。
        return new PlanValidationIssue(code, nodeId, fieldPath, message);
    }

    private static boolean isBlank(String value) {
        // 空白字符串不能作为计划标识、字段名或引用来源；接受它会把结构问题推迟到执行期。
        return value == null || value.isBlank();
    }
}
