package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyCandidate;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPersistenceJsonFactoryTest {
    @Test
    void shouldBuildCardEventFromSafeRecommendationFactsOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);
        Instant dataAt = Instant.parse("2026-08-06T07:00:00Z");
        RecommendationReplyFacts facts = new RecommendationReplyFacts(
                "rank-v1", List.of(new RecommendationReplyCandidate(
                        "movie-1", "cinema-1", "show-1", "68.00", dataAt.plusSeconds(3600),
                        "recommendation", false, true)), true, List.of(), "recommendation", dataAt,
                dataAt.plusSeconds(300), false, false);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "可购场次", AgentReplyMessageType.PLAN_CARD, facts)).value());

        assertThat(payload.path("type").asText()).isEqualTo("PLAN_CARD");
        assertThat(payload.path("title").asText()).isEqualTo("推荐场次");
        assertThat(payload.path("plans")).hasSize(1);
        assertThat(payload.toString()).doesNotContain("seat", "actionId", "parameterHash", "token");
    }

    @Test
    void shouldBuildSelectSeatsBusinessIntentCardWithValidatedShowId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "已确认场次，可选座", AgentReplyMessageType.SELECT_SEATS,
                new SelectSeatsReplyFacts("show-70001"))).value());

        assertThat(payload.path("type").asText()).isEqualTo("BUSINESS_INTENT");
        assertThat(payload.path("payload").path("intent").asText()).isEqualTo("SELECT_SEATS");
        assertThat(payload.path("payload").path("businessRef").path("showId").asText())
                .isEqualTo("show-70001");
    }
}
