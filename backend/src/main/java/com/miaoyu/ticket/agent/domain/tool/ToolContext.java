package com.miaoyu.ticket.agent.domain.tool;

import java.util.List;
import java.util.Objects;

/**
 * Agent 调用工具时携带的最小运行上下文。
 *
 * <p>上下文用于关联运行、限制预算和声明已使用的输入来源，不是用户会话或完整计划副本。工具不得从它
 * 推断用户身份、订单状态或模型提示词；需要这些事实的写能力必须走单独的受控应用服务。
 *
 * @param runId 本轮 Agent 运行关联标识
 * @param nodeId 当前计划节点标识，便于审计一次工具调用属于哪个节点
 * @param targetName 服务端已登记的工具名，不能由工具实现改写为其他目标
 * @param inputRefs 已声明的输入引用路径，不携带与本次工具无关的槽位
 * @param deadlineMs 剩余调用预算，必须为正数且工具实现只能进一步缩小
 * @param traceId 排查日志的关联标识，不含用户敏感信息
 * @param clientRequestId 写工具的客户端关联标识；只读工具可以为空
 * @param idempotencyKey 写工具的幂等键；只读工具可以为空
 * @param stateVersion 使用的槽位/状态快照版本，供结果审计而非状态写入
 */
public record ToolContext(
        String runId,
        String nodeId,
        String targetName,
        List<String> inputRefs,
        long deadlineMs,
        String traceId,
        String clientRequestId,
        String idempotencyKey,
        Long stateVersion) {

    public ToolContext {
        requireText(runId, "runId");
        requireText(nodeId, "nodeId");
        requireText(targetName, "targetName");
        requireText(traceId, "traceId");
        if (deadlineMs <= 0L) {
            // 已耗尽预算的调用不能进入下游，否则会在请求超时后继续占用工具资源。
            throw new IllegalArgumentException("deadlineMs 必须大于 0");
        }
        // 复制引用清单，防止调用方在工具执行期间追加未经计划校验的输入来源。
        inputRefs = List.copyOf(Objects.requireNonNull(inputRefs, "inputRefs 不能为空"));
        inputRefs.forEach(inputRef -> requireText(inputRef, "inputRefs 元素"));
    }

    /**
     * 写工具调用必须复用客户端请求标识和幂等键。
     *
     * <p>这只是基础参数检查，不能替代数据库唯一约束和结果查询恢复。只读工具不应为了满足该方法而
     * 人工生成幂等键。
     */
    public void requireWriteRequestIdentifiers() {
        requireText(clientRequestId, "clientRequestId");
        requireText(idempotencyKey, "idempotencyKey");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            // 统一拒绝空白标识，避免日志和运行快照中出现不可关联的“空调用”。
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
