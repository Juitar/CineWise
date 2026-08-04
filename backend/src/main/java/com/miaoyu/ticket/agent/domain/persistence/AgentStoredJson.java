package com.miaoyu.ticket.agent.domain.persistence;

import java.util.Objects;

/**
 * 已经过白名单筛选的 JSON 文本。
 *
 * <p>此类型只允许 B 的 Application 层传入已经筛选的计划引用、槽位或展示事实。模型原文、工具完整
 * 响应、异常对象、认证秘密和精确位置不得构造为该类型。
 */
public record AgentStoredJson(String value) {

    public AgentStoredJson {
        Objects.requireNonNull(value, "JSON 文本不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("JSON 文本不能为空白");
        }
    }
}
