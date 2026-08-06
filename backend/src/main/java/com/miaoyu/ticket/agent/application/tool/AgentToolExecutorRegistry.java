package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 将白名单定义和类型化执行器成对登记，拒绝仅有名称的伪注册。 */
public final class AgentToolExecutorRegistry {
    private final Map<String, AgentToolExecutor> executors;

    public AgentToolExecutorRegistry(ToolRegistry toolRegistry,
            Collection<? extends AgentToolExecutor<?, ?>> executors) {
        Objects.requireNonNull(toolRegistry, "工具白名单不能为空");
        Map<String, AgentToolExecutor<?, ?>> mapped = new LinkedHashMap<>();
        for (AgentToolExecutor<?, ?> executor : Objects.requireNonNull(executors, "工具执行器不能为空")) {
            AgentToolExecutor<?, ?> checked = Objects.requireNonNull(executor, "工具执行器元素不能为空");
            ToolDefinition definition = Objects.requireNonNull(checked.definition(), "工具定义不能为空");
            ToolDefinition registered = toolRegistry.find(checked.targetName())
                    .orElseThrow(() -> new IllegalArgumentException("执行器工具未进入白名单: " + checked.targetName()));
            if (!checked.targetName().equals(definition.name())
                    || !registered.equals(definition) || !registered.readOnly()) {
                throw new IllegalArgumentException("执行器与只读白名单定义不匹配: " + checked.targetName());
            }
            if (mapped.putIfAbsent(checked.targetName(), checked) != null) {
                throw new IllegalArgumentException("工具执行器重复: " + checked.targetName());
            }
        }
        this.executors = Map.copyOf(mapped);
    }

    public AgentToolExecutor<?, ?> require(String targetName) {
        AgentToolExecutor<?, ?> executor = executors.get(targetName);
        if (executor == null) {
            throw new IllegalStateException("缺少已登记的只读工具适配器: " + targetName);
        }
        return executor;
    }
}
