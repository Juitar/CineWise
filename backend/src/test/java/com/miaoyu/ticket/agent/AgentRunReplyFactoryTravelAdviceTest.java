package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFacts;
import com.miaoyu.ticket.agent.application.run.AgentRunReplyFactory;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.travel.api.TravelAdviceToolResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunReplyFactoryTravelAdviceTest {
    @Test
    void shouldMapSuccessfulStructuredTravelAdviceToCardReply() {
        TravelAdviceToolResult advice = new TravelAdviceToolResult(true, "90001", "READY",
                new TravelAdviceToolResult.Weather("长沙", "小雨", "注意路面湿滑"),
                List.of(new TravelAdviceToolResult.Advice("TRANSPORT", "建议提前出发")), "snapshot",
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T11:00:00+08:00"), false, false, null);
        ToolResult<TravelAdviceToolResult> result = new ToolResult<>(ToolStatus.SUCCESS, advice, null, false,
                false, "RENDER_RESULT", false, null, 1L, advice.dataAt().toInstant(), advice.expiresAt().toInstant());
        CandidatePlan plan = new CandidatePlan("plan-1", 1, List.of());
        MultiToolSupervisorResult supervisorResult = new MultiToolSupervisorResult(plan,
                PlanValidationResult.valid(new ExecutionPlan("plan-1", 1, List.of())), null,
                List.of(new MultiToolSupervisorResult.NodeToolResult("travel", "getTravelAdvice", result)), false,
                null);

        var reply = AgentRunReplyFactory.from(supervisorResult, Instant.parse("2026-08-07T02:30:00Z"));

        assertThat(reply.messageType()).isEqualTo(AgentReplyMessageType.TRAVEL_ADVICE_CARD);
        assertThat(reply.payload()).isInstanceOf(TravelAdviceCardFacts.class);
        TravelAdviceCardFacts facts = (TravelAdviceCardFacts) reply.payload();
        assertThat(facts.taskId()).isEqualTo("90001");
        assertThat(facts.source()).isEqualTo("snapshot");
        assertThat(facts.advice()).extracting(TravelAdviceCardFacts.Advice::text).containsExactly("建议提前出发");
    }
}
