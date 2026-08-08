package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.api.AgentCardPayloadResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardItem;
import com.miaoyu.ticket.agent.application.reply.RelaxationSuggestionFacts;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFacts;
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
                "你想在哪个城市看电影？", AgentReplyMessageType.QUESTION,
                QuestionReplyFacts.fromToolSlot("cityCode")),
                LocalDateTime.of(2026, 8, 7, 10, 0)).value());

        assertThat(payload.path("type").asText()).isEqualTo("QUESTION");
        assertThat(payload.path("questionId").isTextual()).isTrue();
        assertThat(payload.path("questionKind").asText()).isEqualTo("CITY");
        assertThat(payload.path("input").path("name").asText()).isEqualTo("城市");
        assertThat(payload.path("options").isArray()).isTrue();
        assertThat(payload.path("expiresAt").asText()).isEqualTo("2026-08-07T10:10+08:00");
        assertThat(payload.toString()).doesNotContain("cityCode", "travelTaskId", "runId", "actionId");
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
                        dataAt.plusSeconds(300), false, true, 1200)), List.of(),
                new RelaxationSuggestionFacts("DISTANCE", "可适当扩大距离范围"), true, "recommendation", dataAt,
                dataAt.plusSeconds(300), false, false);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "可购场次", AgentReplyMessageType.PLAN_CARD, facts)).value());

        assertThat(payload.path("type").asText()).isEqualTo("PLAN_CARD");
        assertThat(payload.path("title").asText()).isEqualTo("推荐场次");
        assertThat(payload.path("plans")).hasSize(1);
        AgentCardPayloadResponse response = AgentCardPayloadResponse.from(payload);
        JsonNode serialized = mapper.valueToTree(response);
        assertThat(response).isInstanceOf(AgentCardPayloadResponse.PlanCard.class);
        assertThat(serialized).isEqualTo(payload);
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

    @Test
    void shouldBuildTravelAdviceCardWithoutInternalJsonOrLocation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(mapper);
        Instant dataAt = Instant.parse("2026-08-07T02:00:00Z");
        TravelAdviceCardFacts facts = new TravelAdviceCardFacts("90001", "READY", true,
                new TravelAdviceCardFacts.Weather("长沙", "小雨", "注意路面湿滑"),
                List.of(new TravelAdviceCardFacts.Advice("TRANSPORT", "建议提前出发")), "snapshot", true,
                "WEATHER_UNAVAILABLE", dataAt, dataAt.plusSeconds(300), false);

        JsonNode payload = mapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "已查询到出行建议。", AgentReplyMessageType.TRAVEL_ADVICE_CARD, facts)).value());

        assertThat(payload.path("type").asText()).isEqualTo("TRAVEL_ADVICE_CARD");
        assertThat(payload.path("source").asText()).isEqualTo("snapshot");
        assertThat(payload.path("advice")).hasSize(1);
        assertThat(payload.toString()).doesNotContain("weatherJson", "adviceJson", "userId", "latitude",
                "longitude", "polyline", "waypoints");
        assertThat(AgentCardPayloadResponse.from(payload))
                .isInstanceOf(AgentCardPayloadResponse.TravelAdviceCard.class);
    }
}
