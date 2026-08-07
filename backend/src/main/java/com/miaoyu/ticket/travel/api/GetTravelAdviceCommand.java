package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;

/**
 * Agent 查询出行建议的受控输入。
 *
 * <p>执行适配器只能从 B 已确认的 {@code travelTaskId} 槽位构造该对象，
 * 不接受模型自由生成的任务号。</p>
 */
public record GetTravelAdviceCommand(String taskId) implements ToolCommand {
}
