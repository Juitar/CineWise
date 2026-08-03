package com.miaoyu.ticket.agent.domain.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 不可变工具白名单，禁止将模型输出解释为可执行的 Java 名称。 */
public final class ToolRegistry {
    private final Map<String, ToolDefinition> definitions;

    public ToolRegistry(Collection<ToolDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions 不能为空");
        Map<String, ToolDefinition> copiedDefinitions = new LinkedHashMap<>();
        for (ToolDefinition definition : definitions) {
            ToolDefinition previous = copiedDefinitions.putIfAbsent(
                    Objects.requireNonNull(definition, "工具定义不能为空").name(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("工具名称重复: " + definition.name());
            }
        }
        this.definitions = Map.copyOf(copiedDefinitions);
    }

    /** 仅按精确名称查找白名单中的工具定义。 */
    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    /** 返回不可修改的白名单快照。 */
    public Map<String, ToolDefinition> definitions() {
        return definitions;
    }
}
