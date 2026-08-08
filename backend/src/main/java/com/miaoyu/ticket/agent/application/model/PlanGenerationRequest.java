package com.miaoyu.ticket.agent.application.model;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用层向模型网关发出的候选计划请求，只携带本次任务已确认的槽位。
 *
 * <p>它不是用户会话快照：不包含 userId、Cookie、认证信息、历史消息或工具执行结果，避免模型端口
 * 因为“为了生成计划”而获得不必要的个人信息和运行细节。
 *
 * @param clientRequestId 调用方生成的关联标识，仅用于关联本次请求，不能作为写操作幂等键
 * @param input 用户当前输入原文；模型适配器不得把它拼成工具参数而绕过槽位校验
 * @param confirmedSlots 已由服务端确认的字符串槽位快照，构造时复制以防调用方后续修改
 * @param allowedTools 本次可见的只读工具安全 schema，空集合表示模型没有可调用工具
 * @param conversationContext 上一轮原始需求的脱敏语义，只帮助理解指代，不能作为 Tool 参数
 */
public record PlanGenerationRequest(
        String clientRequestId,
        String input,
        Map<String, String> confirmedSlots,
        List<PlanningToolDefinition> allowedTools,
        List<ProfileContextTag> profileTags,
        String conversationContext) {

    public PlanGenerationRequest(String clientRequestId, String input, Map<String, String> confirmedSlots,
            List<PlanningToolDefinition> allowedTools, List<ProfileContextTag> profileTags) {
        this(clientRequestId, input, confirmedSlots, allowedTools, profileTags, null);
    }

    public PlanGenerationRequest(String clientRequestId, String input, Map<String, String> confirmedSlots,
            Set<String> allowedToolNames) {
        this(clientRequestId, input, confirmedSlots,
                allowedToolNames == null ? List.of()
                        : allowedToolNames.stream().sorted().map(PlanningToolDefinition::nameOnly).toList(),
                List.of(), null);
    }

    public PlanGenerationRequest(String clientRequestId, String input, Map<String, String> confirmedSlots,
            Set<String> allowedToolNames, List<ProfileContextTag> profileTags) {
        this(clientRequestId, input, confirmedSlots,
                allowedToolNames == null ? List.of()
                        : allowedToolNames.stream().sorted().map(PlanningToolDefinition::nameOnly).toList(),
                profileTags, null);
    }

    public PlanGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        // 空集合表示“尚无已确认信息”，不能使用 null 让模型适配器自行决定默认槽位或默认工具。
        confirmedSlots = confirmedSlots == null ? Map.of() : Map.copyOf(confirmedSlots);
        // 复制白名单保证本次计划生成期间权限不因外部集合被修改而扩大。
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        profileTags = profileTags == null ? List.of() : List.copyOf(profileTags);
        conversationContext = conversationContext == null || conversationContext.isBlank()
                ? null : conversationContext;
    }

    public Set<String> allowedToolNames() {
        return allowedTools.stream().map(PlanningToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
