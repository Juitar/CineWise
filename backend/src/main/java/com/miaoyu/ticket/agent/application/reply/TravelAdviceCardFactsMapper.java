package com.miaoyu.ticket.agent.application.reply;

import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.travel.api.TravelAdviceToolResult;
import java.util.List;

/** D 的结构化 Tool 结果进入 Agent 回复边界时只复制允许展示的字段。 */
public final class TravelAdviceCardFactsMapper {
    private TravelAdviceCardFactsMapper() {
    }

    public static TravelAdviceCardFacts from(ToolResult<TravelAdviceToolResult> result) {
        if (result == null || !(result.data() instanceof TravelAdviceToolResult advice)) {
            throw new IllegalArgumentException("出行建议 Tool 成功结果不能为空");
        }
        if (!advice.available()) {
            return new TravelAdviceCardFacts(advice.taskId(), advice.taskStatus(), false, null, List.of(),
                    advice.source(), false,
                    null, null, null, advice.expired());
        }
        TravelAdviceCardFacts.Weather weather = advice.weather() == null ? null
                : new TravelAdviceCardFacts.Weather(advice.weather().area(), advice.weather().condition(),
                        advice.weather().risk());
        return new TravelAdviceCardFacts(advice.taskId(), advice.taskStatus(), true, weather,
                advice.advice().stream()
                        .map(item -> new TravelAdviceCardFacts.Advice(item.type(), item.text()))
                        .toList(),
                advice.source(), advice.degraded(), advice.fallbackType(),
                advice.dataAt() == null ? null : advice.dataAt().toInstant(),
                advice.expiresAt() == null ? null : advice.expiresAt().toInstant(), advice.expired());
    }
}
