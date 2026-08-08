package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentPlanCardFollowUpResolver;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentPlanCardFollowUpResolverTest {
    @Test
    void shouldExplainTheReferencedPersistedPlanWithoutExposingInternalIds() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        when(messages.findLatestPlanCardBySessionIdAndUserId(10L, 9L))
                .thenReturn(Optional.of(planCard()));
        AgentPlanCardFollowUpResolver resolver = new AgentPlanCardFollowUpResolver(messages, new ObjectMapper());

        var resolved = resolver.resolve(10L, 9L,
                "基于当前最新推荐中的第 2 个方案，请解释这个方案，并说明是否需要继续调整。");

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().messageType()).isEqualTo(AgentReplyMessageType.TEXT);
        assertThat(resolved.orElseThrow().text())
                .contains("第2个方案", "蜘蛛侠", "万达影城", "不必继续调整")
                .doesNotContain("movie-2", "cinema-2", "show-2");
    }

    @Test
    void shouldReturnSafeTextForMissingCardOrInvalidIndex() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentPlanCardFollowUpResolver resolver = new AgentPlanCardFollowUpResolver(messages, new ObjectMapper());

        var missing = resolver.resolve(10L, 9L, "请解释第2个方案，并说明是否需要调整");

        assertThat(missing.orElseThrow().text()).contains("没有可解释的推荐方案");
        when(messages.findLatestPlanCardBySessionIdAndUserId(10L, 9L))
                .thenReturn(Optional.of(planCard()));

        var invalid = resolver.resolve(10L, 9L, "请解释第3个方案，并说明是否需要调整");

        assertThat(invalid.orElseThrow().text()).contains("只有 2 个方案");
    }

    @Test
    void shouldIgnoreOrdinaryMovieRequestsWithoutAPlanExplanationReference() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentPlanCardFollowUpResolver resolver = new AgentPlanCardFollowUpResolver(messages, new ObjectMapper());

        assertThat(resolver.resolve(10L, 9L, "再推荐两个电影方案")).isEmpty();
        verify(messages, never()).findLatestPlanCardBySessionIdAndUserId(10L, 9L);
    }

    private static AgentMessage planCard() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 8, 0);
        String payload = """
                {
                  "schemaVersion":"v1","algorithmVersion":"a1","degraded":false,"expired":false,
                  "plans":[
                    {"movieId":"movie-1","movieName":"流浪地球","cinemaId":"cinema-1",
                     "cinemaName":"星美影城","showId":"show-1","price":"39.00","currency":"CNY",
                     "startTime":"2026-08-08T10:00:00Z","rating":"8.5","reasons":["距离较近"],
                     "expired":false,"purchaseEligible":true},
                    {"movieId":"movie-2","movieName":"蜘蛛侠","cinemaId":"cinema-2",
                     "cinemaName":"万达影城","showId":"show-2","price":"45.00","currency":"CNY",
                     "startTime":"2026-08-08T12:00:00Z","rating":"8.8","reasons":["符合影片偏好"],
                     "expired":false,"purchaseEligible":true}
                  ]
                }
                """;
        return new AgentMessage(20L, "message-20", 10L, 11L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.PLAN_CARD, "已找到 2 个方案", new AgentStoredJson(payload),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
    }
}
