package com.miaoyu.ticket.agent.domain.run;

import java.util.Objects;

/** 重规划额度检查结果；本阶段不负责生成或替换计划。 */
public record ReplanRequestResult(boolean approved, ExecutionRunState state) {

    public ReplanRequestResult {
        Objects.requireNonNull(state, "运行状态不能为空");
    }
}
