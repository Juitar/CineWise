package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;

/**
 * Agent 已接入工具的统一执行注册接口。
 *
 * <p>每个实现仍固定自己的 Command 和公开 Application Tool；这个接口只统一登记和调度，不能用字符串
 * 构造任意 Command 或查找 Spring Bean。
 */
public interface AgentToolExecutor<C extends ToolCommand, R> extends ReadOnlyToolExecutionAdapter {
    /** 返回与执行器一一对应的白名单定义，启动时会与 ToolRegistry 逐项比对。 */
    ToolDefinition definition();

    /** 类型化 Command/Result 入口；状态适配方法只能负责从已校验快照组装参数。 */
    ToolResult<R> execute(ToolContext context, C command);
}
