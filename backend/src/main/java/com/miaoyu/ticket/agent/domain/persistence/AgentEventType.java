package com.miaoyu.ticket.agent.domain.persistence;

/** 可持久化的 Agent 运行事件；重置和心跳只属于 SSE 协议，不写入本表。 */
public enum AgentEventType {
    MESSAGE_START("message.start"),
    MESSAGE_DELTA("message.delta"),
    PLAN_CREATED("plan.created"),
    PLAN_REPLANNED("plan.replanned"),
    STEP_START("step.start"),
    STEP_COMPLETE("step.complete"),
    STEP_FAILED("step.failed"),
    TOOL_START("tool.start"),
    TOOL_COMPLETE("tool.complete"),
    TOOL_ERROR("tool.error"),
    /** 旧事件值保留给历史事件读取，不再由新运行写入。 */
    TOOL_RESULT("tool.result"),
    CARD("card"),
    MESSAGE_COMPLETE("message.complete"),
    MESSAGE_ERROR("message.error"),
    RUN_COMPLETE("run.complete");

    private final String wireValue;

    AgentEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
