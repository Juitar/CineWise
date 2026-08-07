package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.api.AgentCardPayloadResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C 的流消费者夹具必须保留全部固定字段，包括可空的恢复字段。 */
class AgentCFixtureContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepFiveStableEventFixturesForAgentWorkspace() throws Exception {
        List<String> fixtureNames = List.of(
                "recommendation-card.json", "processing-event.json", "error-event.json", "duplicate-event.json",
                "stream-reset.json");

        for (String fixtureName : fixtureNames) {
            JsonNode fixture = fixture(fixtureName);
            assertThat(fixture.path("eventId").isTextual()).isTrue();
            assertThat(fixture.path("sessionId").isTextual()).isTrue();
            assertThat(fixture.path("runId").isTextual()).isTrue();
            assertThat(fixture.has("planId")).isTrue();
            assertThat(fixture.has("planVersion")).isTrue();
            assertThat(fixture.has("nodeId")).isTrue();
            assertThat(fixture.path("eventType").isTextual()).isTrue();
            assertThat(fixture.path("displayText").isTextual()).isTrue();
            assertThat(fixture.path("payload").isObject()).isTrue();
            assertThat(fixture.has("occurredAt")).isTrue();
        }
    }

    @Test
    void shouldKeepRunRecoveryFixtureCompatibleWithTheSharedResultEnvelope() throws Exception {
        JsonNode fixture = fixture("run-completed.json");

        assertThat(fixture.path("code").asInt()).isZero();
        assertThat(fixture.path("message").isTextual()).isTrue();
        assertThat(fixture.path("traceId").isTextual()).isTrue();
        assertThat(fixture.path("data").path("lastEventId").isTextual()).isTrue();
        assertThat(fixture.path("data").path("messages").isArray()).isTrue();
        assertThat(fixture.path("data").path("steps").isArray()).isTrue();
        assertThat(fixture.path("data").path("events").isArray()).isTrue();
    }

    @Test
    void shouldUseStreamResetWatermarkAsTheSessionResumeCursor() throws Exception {
        JsonNode reset = fixture("stream-reset.json");
        JsonNode run = fixture("run-completed.json");

        assertThat(reset.path("eventType").asText()).isEqualTo("stream.reset");
        assertThat(reset.path("eventId").isTextual()).isTrue();
        assertThat(reset.path("payload").path("watermark").asText()).isEqualTo(reset.path("eventId").asText());
        assertThat(run.path("data").path("lastEventId").isTextual()).isTrue();
    }

    @Test
    void shouldKeepSessionManagementFixturesCompatibleWithSharedEnvelope() throws Exception {
        JsonNode created = fixture("session-created.json");
        JsonNode sessions = fixture("session-list.json");
        JsonNode messages = fixture("session-message-history.json");
        JsonNode cleared = fixture("session-cleared.json");
        JsonNode bulkCleared = fixture("sessions-bulk-cleared.json");

        assertThat(created.path("data").path("sessionId").isTextual()).isTrue();
        assertThat(created.path("data").has("activeRunId")).isFalse();
        assertThat(sessions.path("data").path("records").isArray()).isTrue();
        assertThat(messages.path("data").path("records").get(0).path("messageId").isTextual()).isTrue();
        assertThat(messages.path("data").path("records").get(0).path("runId").isTextual()).isTrue();
        assertThat(messages.path("data").path("records").get(0).path("runId").asText())
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(cleared.path("data").path("cleared").isBoolean()).isTrue();
        assertThat(bulkCleared.path("data").path("clearedCount").canConvertToInt()).isTrue();
        assertThat(bulkCleared.path("data").path("skippedCount").canConvertToInt()).isTrue();
    }

    @Test
    void shouldKeepRunCancelFixturesIdempotentAndStringIdentified() throws Exception {
        JsonNode cancelled = fixture("run-cancelled.json");
        JsonNode terminal = fixture("run-terminal-cancel.json");

        assertThat(cancelled.path("data").path("runId").isTextual()).isTrue();
        assertThat(cancelled.path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(terminal.path("data").path("runId").isTextual()).isTrue();
        assertThat(terminal.path("data").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldKeepDistanceRunRecoveryFixtureFreeOfLocationContext() throws Exception {
        JsonNode waiting = fixture("distance-run-waiting.json");
        assertThat(waiting.path("code").asInt()).isZero();
        assertThat(waiting.path("data").path("runId").isTextual()).isTrue();
        assertThat(waiting.path("data").path("status").asText()).isEqualTo("WAITING_LOCATION");
        assertThat(waiting.path("data").path("lastEventId").isTextual()).isTrue();
        assertThat(waiting.toString()).doesNotContain("distanceContextId", "latitude", "longitude", "address");
    }

    @Test
    void shouldKeepQuestionPlanIntentAndConfirmationContractsDirectlyRenderable() throws Exception {
        JsonNode question = fixture("question-card.json");
        JsonNode plan = fixture("plan-card.json");
        JsonNode businessIntent = fixture("business-intent-card.json");
        JsonNode confirmation = fixture("order-confirm-card.json");
        JsonNode confirmationApi = fixture("confirmation-api-fixtures.json");

        assertThat(question.path("eventType").asText()).isEqualTo("card");
        assertThat(question.path("payload").path("type").asText()).isEqualTo("QUESTION");
        assertThat(question.path("payload").path("input").path("name").isTextual()).isTrue();
        assertThat(plan.path("payload").path("type").asText()).isEqualTo("PLAN_CARD");
        assertThat(plan.path("payload").path("plans").isArray()).isTrue();
        assertThat(businessIntent.path("payload").path("type").asText()).isEqualTo("BUSINESS_INTENT");
        assertThat(businessIntent.path("payload").path("payload").path("businessRef").path("showId").isTextual())
                .isTrue();
        AgentPersistenceJsonFactory factory = new AgentPersistenceJsonFactory(objectMapper);
        JsonNode productionPayload = objectMapper.readTree(factory.cardPayload(new ReplyGenerationResponse(
                "前往选座", AgentReplyMessageType.SELECT_SEATS,
                new SelectSeatsReplyFacts("3001", "1001", "2001"))).value());
        assertThat(businessIntent.path("payload")).isEqualTo(productionPayload);
        assertThat(confirmation.path("payload").path("type").asText()).isEqualTo("PLAN_CARD");
        assertThat(confirmation.path("payload").path("actionId").isTextual()).isTrue();
        assertThat(confirmation.path("payload").path("status").isTextual()).isTrue();
        assertThat(confirmationApi.path("success").path("data").path("runId").isTextual()).isTrue();
        assertThat(confirmationApi.path("resultUnknown").path("data").path("status").asText())
                .isEqualTo("RESULT_UNKNOWN");
    }

    @Test
    void shouldMapCardFixturesToTheFormalPayloadDtosWithoutChangingTheirJson() throws Exception {
        for (String fixtureName : List.of("question-card.json", "plan-card.json", "business-intent-card.json",
                "order-confirm-card.json", "travel-advice-card.json", "travel-advice-unavailable-card.json",
                "travel-advice-degraded-expired-card.json")) {
            JsonNode payload = fixture(fixtureName).path("payload");
            AgentCardPayloadResponse response = AgentCardPayloadResponse.from(payload);

            assertThat(response).isNotInstanceOf(AgentCardPayloadResponse.Unknown.class);
            JsonNode serialized = objectMapper.valueToTree(response);
            assertThat(serialized).isEqualTo(payload);
        }
    }

    @Test
    void shouldKeepTravelAdviceFixturesSafeAndDirectlyRenderable() throws Exception {
        for (String fixtureName : List.of("travel-advice-card.json", "travel-advice-unavailable-card.json",
                "travel-advice-degraded-expired-card.json")) {
            JsonNode payload = fixture(fixtureName).path("payload");
            assertThat(payload.path("type").asText()).isEqualTo("TRAVEL_ADVICE_CARD");
            assertThat(payload.path("taskId").asText()).matches("^[1-9][0-9]*$");
            assertThat(payload.path("weatherJson").isMissingNode()).isTrue();
            assertThat(payload.path("adviceJson").isMissingNode()).isTrue();
            assertThat(payload.toString()).doesNotContain("userId", "latitude", "longitude", "polyline", "waypoints");
        }
    }

    private JsonNode fixture(String fixtureName) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/agent/c/" + fixtureName)) {
            assertThat(input).as("夹具必须存在: %s", fixtureName).isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
