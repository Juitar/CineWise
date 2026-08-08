package com.miaoyu.ticket.agent.domain.tool;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务端登记的工具元数据，用于白名单路由和计划校验。
 *
 * <p>定义是 B 的执行许可，不是给模型随意选择的能力描述。它同时固定 Command/结果 Java 类型、
 * 是否只读、预算、输入字段和可公开错误码，工具适配器必须依此实现类型化调用。
 */
public record ToolDefinition(
        String name,
        String purpose,
        Class<? extends ToolCommand> commandType,
        Class<?> resultType,
        boolean readOnly,
        Duration timeout,
        boolean idempotencyRequired,
        List<ToolInputDefinition> inputs,
        Set<Integer> exposedErrorCodes) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("工具用途不能为空");
        }
        Objects.requireNonNull(commandType, "Command 类型不能为空");
        Objects.requireNonNull(resultType, "结果类型不能为空");
        timeout = Objects.requireNonNull(timeout, "超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            // 无上限预算会让单个模型计划拖长请求；超时值必须在定义时明确。
            throw new IllegalArgumentException("超时必须大于 0");
        }
        if (!readOnly && !idempotencyRequired) {
            // 写工具必须先声明幂等要求，防止未来错误把确认或交易操作当成普通工具执行。
            throw new IllegalArgumentException("写工具必须声明幂等要求");
        }
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs 不能为空"));
        Set<String> names = inputs.stream().map(ToolInputDefinition::name).collect(Collectors.toSet());
        if (names.size() != inputs.size()) {
            // 重名输入会使计划引用和 Command 赋值不确定，不能依赖列表顺序“碰巧正确”。
            throw new IllegalArgumentException("工具输入字段名称不能重复");
        }
        exposedErrorCodes = Set.copyOf(Objects.requireNonNull(exposedErrorCodes, "错误码集合不能为空"));
        if (exposedErrorCodes.stream().anyMatch(code -> code == null || code <= 0)) {
            // 只允许稳定正数业务码进入 Agent 回复；异常对象和 HTTP 状态不在这里透传。
            throw new IllegalArgumentException("错误码必须为正数");
        }
    }

    /** 兼容内部测试夹具；生产工具必须在定义处显式提供用途。 */
    public ToolDefinition(
            String name,
            Class<? extends ToolCommand> commandType,
            Class<?> resultType,
            boolean readOnly,
            Duration timeout,
            boolean idempotencyRequired,
            List<ToolInputDefinition> inputs,
            Set<Integer> exposedErrorCodes) {
        this(name, name, commandType, resultType, readOnly, timeout, idempotencyRequired, inputs,
                exposedErrorCodes);
    }

    /**
     * 仅在写工具调用前检查必须复用的请求标识。
     *
     * <p>只读工具不能被这项校验阻塞；写工具即使通过也仍需在自身应用服务中验证当前用户、状态和数据库
     * 幂等约束。
     */
    public void validateContext(ToolContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!readOnly) {
            context.requireWriteRequestIdentifiers();
        }
    }

    /**
     * 返回计划必须提供的输入字段定义。
     *
     * <p>顺序保留给追问流程使用；调用方不应转换为无序集合，否则“下一项缺失槽位”可能每次不同。
     */
    public List<ToolInputDefinition> requiredInputs() {
        return inputs.stream().filter(ToolInputDefinition::required).toList();
    }
}
