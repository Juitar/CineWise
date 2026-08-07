package com.miaoyu.ticket.agent.application.audit;

import java.util.List;

/** 管理运行列表的稳定分页投影。 */
public record AdminAgentRunPageView(long total, int page, int size, List<AdminAgentRunView> records) {
    public AdminAgentRunPageView {
        records = List.copyOf(records);
    }
}
