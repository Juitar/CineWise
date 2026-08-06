package com.miaoyu.ticket.agent.domain.tool;

/** Owner DTO 尚未确认时的空 Command，仅用于测试注册，不得作为生产业务参数。 */
public record DeferredAgentToolCommand() implements ToolCommand {
}
