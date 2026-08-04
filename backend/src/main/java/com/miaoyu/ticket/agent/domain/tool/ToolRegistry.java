package com.miaoyu.ticket.agent.domain.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 不可变工具白名单，禁止将模型输出解释为可执行的 Java 名称。
 *
 * <p>注册表只保存服务端构造的 ToolDefinition；模型或前端提交的工具名只能用于精确匹配，不能触发
 * Bean 查询、反射调用、动态类加载或跨模块 Controller 调用。
 */
public final class ToolRegistry {
    private final Map<String, ToolDefinition> definitions;

    public ToolRegistry(Collection<ToolDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions 不能为空");
        Map<String, ToolDefinition> copiedDefinitions = new LinkedHashMap<>();
        for (ToolDefinition definition : definitions) {
            // 按声明顺序复制方便追问稳定，但最终暴露不可变快照，调用方不能运行时增删工具。
            ToolDefinition previous = copiedDefinitions.putIfAbsent(
                    Objects.requireNonNull(definition, "工具定义不能为空").name(), definition);
            if (previous != null) {
                // 重名定义会让同一 targetName 对应不同类型或权限，启动阶段必须立即拒绝。
                throw new IllegalArgumentException("工具名称重复: " + definition.name());
            }
        }
        this.definitions = Map.copyOf(copiedDefinitions);
    }

    /** 仅按精确名称查找白名单中的工具定义；不做大小写、别名或前缀匹配。 */
    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    /** 返回不可修改的白名单快照；调用方只能读取，不能借此登记临时工具。 */
    public Map<String, ToolDefinition> definitions() {
        return definitions;
    }
}
