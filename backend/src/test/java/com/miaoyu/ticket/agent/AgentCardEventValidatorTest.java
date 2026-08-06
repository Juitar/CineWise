package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.api.AgentCardEventValidator;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 固定卡片事件夹具同时是 B 给 C 的可执行协议样例。 */
class AgentCardEventValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRenderAllSixTypedPayloadsIncludingLocationPermissionQuestion() throws Exception {
        for (String fixture : List.of("text-card.json", "question-card.json", "location-permission-question.json",
                "recommendation-card.json", "plan-card.json", "progress-card.json", "error-card.json")) {
            assertThat(validate(fixture).decision()).isEqualTo(AgentCardEventValidator.Decision.RENDER);
        }
        JsonNode location = fixture("location-permission-question.json").path("payload");
        assertThat(location.path("questionKind").asText()).isEqualTo("LOCATION_PERMISSION");
        assertThat(location.path("locationAuthorization").path("authorizationState").asText())
                .isEqualTo("NOT_REQUESTED");
    }

    @Test
    void shouldSafelyDowngradeUnknownEventAndPayloadTypes() throws Exception {
        assertThat(validate("unknown-event-type.json").decision()).isEqualTo(AgentCardEventValidator.Decision.SAFE_TEXT);
        assertThat(validate("unknown-payload-type.json").decision())
                .isEqualTo(AgentCardEventValidator.Decision.SAFE_TEXT);
    }

    @Test
    void shouldRejectMissingOrWrongTypedFields() throws Exception {
        for (String fixture : List.of("missing-outer-field.json", "missing-payload-field.json", "wrong-field-type.json")) {
            assertThat(validate(fixture).decision()).isEqualTo(AgentCardEventValidator.Decision.REJECT);
        }
    }

    @Test
    void shouldKeepConfirmationRequestAndAllRecoveryResultsEncodable() throws Exception {
        JsonNode fixture = fixture("confirmation-api-fixtures.json");
        assertThat(fixture.path("request").path("confirmed").isBoolean()).isTrue();
        for (String result : List.of("success", "rejected", "resultUnknown")) {
            JsonNode data = fixture.path(result).path("data");
            assertThat(data.path("actionId").isTextual()).isTrue();
            assertThat(data.path("runId").isTextual()).isTrue();
            assertThat(data.path("planVersion").isInt()).isTrue();
        }
        assertThat(fixture.path("expired").path("code").asInt()).isEqualTo(206003);
        assertThat(fixture.path("parametersChanged").path("code").asInt()).isEqualTo(206004);
        assertThat(fixture.path("duplicate").path("code").asInt()).isEqualTo(206006);
    }

    @Test
    void shouldDescribeExpiryDegradeAndResumeSequenceWithoutHistoricalLookup() throws Exception {
        JsonNode sequence = fixture("event-sequence-scenarios.json");
        assertThat(AgentCardEventValidator.validate(sequence.path("expiredCard")).decision())
                .isEqualTo(AgentCardEventValidator.Decision.RENDER);
        assertThat(OffsetDateTime.parse(sequence.path("expiredCard").path("payload").path("expiresAt").asText()))
                .isBefore(OffsetDateTime.parse("2026-08-05T10:00:00+08:00"));
        assertThat(sequence.path("oldPlanVersion").path("planVersion").asInt()).isEqualTo(1);
        assertThat(sequence.path("duplicateEventId").get(0).asText())
                .isEqualTo(sequence.path("duplicateEventId").get(1).asText());
        assertThat(sequence.path("eventGap").get(0).asLong()).isLessThan(sequence.path("eventGap").get(1).asLong());
        assertThat(sequence.path("resetThenResume").get(0).asText()).startsWith("stream.reset:");
    }

    private AgentCardEventValidator.ValidationResult validate(String fixtureName) throws Exception {
        return AgentCardEventValidator.validate(fixture(fixtureName));
    }

    private JsonNode fixture(String fixtureName) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/agent/c/" + fixtureName)) {
            assertThat(input).as("夹具必须存在: %s", fixtureName).isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
