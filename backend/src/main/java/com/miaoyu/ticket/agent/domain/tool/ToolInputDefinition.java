package com.miaoyu.ticket.agent.domain.tool;

import java.util.Objects;

/** 工具 Command 中可由计划提供的一个输入字段定义。 */
public record ToolInputDefinition(String name, Class<?> valueType, boolean required) {

    public ToolInputDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("输入字段名称不能为空");
        }
        Objects.requireNonNull(valueType, "输入字段类型不能为空");
        if (valueType == Object.class) {
            throw new IllegalArgumentException("输入字段不能使用 Object 类型");
        }
    }
}
