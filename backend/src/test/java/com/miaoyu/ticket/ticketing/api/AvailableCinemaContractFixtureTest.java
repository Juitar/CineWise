package com.miaoyu.ticket.ticketing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.PageResult;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 固定夹具确保 C 接入的分页字段、字符串 ID 和稳定错误码不发生漂移。 */
class AvailableCinemaContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void givenAvailableCinemaFixtures_whenParse_thenPreservePublicContract() throws Exception {
        JsonNode success = read("available-cinemas-success.json");
        JsonNode page = success.required("data");
        assertThat(success.required("code").asInt()).isZero();
        assertThat(page.required("total").asInt()).isEqualTo(page.required("records").size());
        JsonNode record = page.required("records").get(0);
        assertThat(record.required("cinemaId").asText()).matches("[1-9][0-9]*");
        assertThat(record.required("availableShowCount").asInt()).isPositive();
        assertThat(record.required("nearestStartTime").asText()).endsWith("+08:00");
        assertThat(read("available-cinemas-empty.json").required("data").required("records")).isEmpty();
        assertThat(read("available-cinemas-invalid-parameter.json").required("code").asInt()).isEqualTo(100001);
        assertThat(read("available-cinemas-query-unavailable.json").required("code").asInt()).isEqualTo(306003);
    }

    private JsonNode read(String fileName) throws Exception {
        try (InputStream input = new ClassPathResource("fixtures/ticketing/c/" + fileName).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }
}
