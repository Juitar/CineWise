package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardItem;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPersistenceJsonFactoryTest {
    @Test
    void shouldBuildRenderableQuestionCardFromTheServerSelectedSlot() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "请补充cityCode。", AgentReplyMessageType.QUESTION, new QuestionReplyFacts("cityCode")),
                LocalDateTime.of(2026, 8, 7, 10, 0)).value());

        assertThat(payload.path("type").asText()).isEqualTo("QUESTION");
        assertThat(payload.path("questionId").isTextual()).isTrue();
        assertThat(payload.path("input").path("name").asText()).isEqualTo("cityCode");
        assertThat(payload.path("options").isArray()).isTrue();
        assertThat(payload.path("expiresAt").asText()).isEqualTo("2026-08-07T10:10+08:00");
    }

    @Test
    void shouldBuildCardEventFromSafeRecommendationFactsOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);
        Instant dataAt = Instant.parse("2026-08-06T07:00:00Z");
        RecommendationPlanCardFacts facts = new RecommendationPlanCardFacts(
                "1.0", "rank-v1", List.of(new RecommendationPlanCardItem(
                        "COMPREHENSIVE", "10001", "影片", "20001", "影院", "30001", "68.00", "CNY",
                        dataAt.plusSeconds(3600), "8.5", 1.0D, List.of("匹配条件"), "recommendation", dataAt,
                        dataAt.plusSeconds(300), false, true, null)), List.of(), null, true, "recommendation", dataAt,
                dataAt.plusSeconds(300), false, false);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "可购场次", AgentReplyMessageType.PLAN_CARD, facts)).value());

        assertThat(payload.path("type").asText()).isEqualTo("PLAN_CARD");
        assertThat(payload.path("title").asText()).isEqualTo("推荐场次");
        assertThat(payload.path("plans")).hasSize(1);
        assertThat(payload.toString()).doesNotContain("seat", "actionId", "parameterHash", "token", "evidence",
                "latitude", "longitude", "address", "estimatedTravelMinutes");
    }

    @Test
    void shouldBuildSelectSeatsBusinessIntentCardWithValidatedBusinessReferences() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "已确认场次，可选座", AgentReplyMessageType.SELECT_SEATS,
                new SelectSeatsReplyFacts("70001", "10001", "20001"))).value());

        assertThat(payload.path("type").asText()).isEqualTo("BUSINESS_INTENT");
        assertThat(payload.path("payload").path("intent").asText()).isEqualTo("SELECT_SEATS");
        assertThat(payload.path("payload").path("businessRef").path("showId").asText())
                .isEqualTo("70001");
        assertThat(payload.path("payload").path("businessRef").path("movieId").asText())
                .isEqualTo("10001");
        assertThat(payload.path("payload").path("businessRef").path("cinemaId").asText())
                .isEqualTo("20001");
    }
}
