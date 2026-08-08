package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 模型只读的工具规划 schema；不包含 Java 类名、超时、错误码或任何业务数据。 */
public record PlanningToolDefinition(
        String name,
        String purpose,
        List<Input> inputs) {

    public PlanningToolDefinition {
        if (name == null || name.isBlank() || purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("规划工具名称和用途不能为空");
        }
        inputs = List.copyOf(Objects.requireNonNull(inputs, "规划工具输入不能为空"));
    }

    public static PlanningToolDefinition from(
            ToolDefinition definition, Set<String> userQuestionableInputs) {
        Objects.requireNonNull(definition, "工具定义不能为空");
        Set<String> questionable = Set.copyOf(userQuestionableInputs);
        return new PlanningToolDefinition(definition.name(), definition.purpose(), definition.inputs().stream()
                .map(input -> input(input, questionable.contains(input.name())))
                .toList());
    }

    public static PlanningToolDefinition nameOnly(String name) {
        return new PlanningToolDefinition(name, name, List.of());
    }

    private static Input input(ToolInputDefinition input, boolean userQuestionable) {
        return new Input(input.name(), jsonType(input.valueType()), input.required(), userQuestionable);
    }

    private static String jsonType(Class<?> type) {
        if (type == Integer.class || type == Long.class || type == BigDecimal.class) {
            return "number";
        }
        if (type == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(type)) {
            return "array";
        }
        if (type == LocalDate.class) {
            return "date";
        }
        if (type == LocalTime.class) {
            return "time";
        }
        return "string";
    }

    public record Input(String name, String type, boolean required, boolean userQuestionable) {
        public Input {
            if (name == null || name.isBlank() || type == null || type.isBlank()) {
                throw new IllegalArgumentException("规划工具输入名称和类型不能为空");
            }
        }
    }
}
