package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AdminAgentRunFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldKeepCAdminRunFixturesOnTheActualResponseShape() throws Exception {
        assertPage("admin-agent-run-page.json");
        var detail = fixture("admin-agent-run-detail.json").path("data");
        assertThat(detail.path("runId").isTextual()).isTrue();
        assertThat(detail.path("sessionId").isTextual()).isTrue();
        assertThat(detail.path("nodes").get(0).path("targetName").asText()).isEqualTo("queryShows");
        assertThat(detail.toString()).doesNotContain("slotSnapshot", "inputRefs", "payload", "latitude", "token");
    }

    private void assertPage(String name) throws Exception {
        var page = fixture(name).path("data");
        assertThat(page.path("records").isArray()).isTrue();
        assertThat(page.path("records").get(0).path("runId").isTextual()).isTrue();
        assertThat(page.path("records").get(0).has("errorSummary")).isTrue();
    }

    private com.fasterxml.jackson.databind.JsonNode fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/agent/c/" + name)) {
            assertThat(input).isNotNull();
            return mapper.readTree(input);
        }
    }
}
