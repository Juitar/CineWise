package com.miaoyu.ticket.agent.application.audit;

import java.time.OffsetDateTime;

/** 管理轨迹列表的 REST 原始筛选输入；具体校验由应用服务统一完成。 */
public record AdminAgentRunListQuery(
        String status,
        String userKeyword,
        OffsetDateTime startedFrom,
        OffsetDateTime startedTo,
        Integer page,
        Integer size) {
}
