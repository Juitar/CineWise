package com.miaoyu.ticket.agent.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 06 Agent 模块稳定错误码，与 Agent 详细设计第 11.7 节一致。 */
public enum AgentErrorCode implements ErrorCode {
    ACTION_EXPIRED(206003, "信息可能已变化，请重新确认", HttpStatus.CONFLICT),
    ACTION_PARAMETER_CHANGED(206004, "操作内容已变化，请重新发起确认", HttpStatus.CONFLICT),
    AGENT_RESOURCE_NOT_FOUND(206005, "会话、运行或消息不存在", HttpStatus.NOT_FOUND),
    ACTION_CONFIRMING(206006, "正在确认操作结果，请勿重复提交", HttpStatus.CONFLICT),
    REQUEST_HASH_MISMATCH(206009, "重复请求参数不一致", HttpStatus.CONFLICT),
    ACTIVE_RUN_CONFLICT(206008, "会话已有活动运行", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AgentErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
