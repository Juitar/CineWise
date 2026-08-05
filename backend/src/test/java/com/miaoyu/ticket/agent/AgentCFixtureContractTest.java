package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private JsonNode fixture(String fixtureName) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/agent/c/" + fixtureName)) {
            assertThat(input).as("夹具必须存在: %s", fixtureName).isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
