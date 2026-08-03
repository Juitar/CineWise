package com.miaoyu.ticket.agent.domain.tool;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 服务端登记的工具元数据，用于白名单路由和计划校验。 */
public record ToolDefinition(
        String name,
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
        Objects.requireNonNull(commandType, "Command 类型不能为空");
        Objects.requireNonNull(resultType, "结果类型不能为空");
        timeout = Objects.requireNonNull(timeout, "超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("超时必须大于 0");
        }
        if (!readOnly && !idempotencyRequired) {
            throw new IllegalArgumentException("写工具必须声明幂等要求");
        }
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs 不能为空"));
        Set<String> names = inputs.stream().map(ToolInputDefinition::name).collect(Collectors.toSet());
        if (names.size() != inputs.size()) {
            throw new IllegalArgumentException("工具输入字段名称不能重复");
        }
        exposedErrorCodes = Set.copyOf(Objects.requireNonNull(exposedErrorCodes, "错误码集合不能为空"));
        if (exposedErrorCodes.stream().anyMatch(code -> code == null || code <= 0)) {
            throw new IllegalArgumentException("错误码必须为正数");
        }
    }

    /** 仅在写工具调用前检查必须复用的请求标识。 */
    public void validateContext(ToolContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!readOnly) {
            context.requireWriteRequestIdentifiers();
        }
    }

    /** 返回计划必须提供的输入字段定义。 */
    public List<ToolInputDefinition> requiredInputs() {
        return inputs.stream().filter(ToolInputDefinition::required).toList();
    }
}
