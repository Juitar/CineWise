package com.miaoyu.ticket.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.ticketing.api.SeatMapResponse;
import com.miaoyu.ticket.ticketing.api.ShowSummaryResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 保护A向B/C交付的票务JSON夹具与实际SpringDoc契约不发生静默漂移。 */
@ActiveProfiles("test")
@SpringBootTest(properties = "cinewise.seed.enabled=false")
@AutoConfigureMockMvc
class TicketingContractFixtureTest {

    private static final String FIXTURE_ROOT = "fixtures/ticketing/";
    private static final Set<String> TOOL_RESULT_FIELDS = Set.of(
            "status",
            "data",
            "errorCode",
            "retryable",
            "replanSuggested",
            "suggestedNextAction",
            "degraded",
            "fallbackType",
            "stateVersion",
            "dataAt",
            "expiresAt");
    private static final List<String> REST_FIXTURES = List.of(
            "c/show-list-success.json",
            "c/seat-map-success.json",
            "c/create-order-success.json",
            "c/order-page-success.json",
            "c/payment-success.json",
            "c/electronic-ticket-success.json",
            "c/refund-impact-success.json",
            "c/refund-success.json",
            "c/alternative-shows-success.json",
            "c/seat-conflict-error.json",
            "c/idempotency-mismatch-error.json",
            "c/order-not-found-error.json",
            "c/unauthenticated-error.json");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenBFixtures_whenParse_thenPreserveToolEnvelopeFreshnessAndRecoverySemantics() throws Exception {
        JsonNode queryShows = readFixture("b/query-shows-success.json");
        assertToolEnvelope(queryShows);
        assertThat(queryShows.required("status").asText()).isEqualTo("SUCCESS");
        assertThat(queryShows.required("data").required("shows").get(0).required("basePrice").asText())
                .matches("\\d+\\.\\d{2}");
        Instant dataAt = Instant.parse(queryShows.required("dataAt").asText());
        Instant expiresAt = Instant.parse(queryShows.required("expiresAt").asText());
        Instant showStartTime = Instant.parse(queryShows.required("data")
                .required("shows")
                .get(0)
                .required("startTime")
                .asText());
        assertThat(expiresAt).isAfter(dataAt).isBeforeOrEqualTo(showStartTime);

        JsonNode createdOrder = readFixture("b/create-order-success.json");
        JsonNode recoveredOrder = readFixture("b/create-order-idempotent-recovery.json");
        assertToolEnvelope(createdOrder);
        assertToolEnvelope(recoveredOrder);
        assertThat(recoveredOrder.required("data")).isEqualTo(createdOrder.required("data"));
        assertThat(recoveredOrder.has("replayed")).isFalse();
        assertThat(recoveredOrder.required("retryable").asBoolean()).isFalse();

        JsonNode seatConflict = readFixture("b/create-order-seat-conflict.json");
        assertToolEnvelope(seatConflict);
        assertThat(seatConflict.required("errorCode").asInt()).isEqualTo(204001);
        assertThat(seatConflict.required("retryable").asBoolean()).isFalse();
        assertThat(seatConflict.required("replanSuggested").asBoolean()).isTrue();

        JsonNode orderNotFound = readFixture("b/query-order-not-found.json");
        assertToolEnvelope(orderNotFound);
        assertThat(orderNotFound.required("errorCode").asInt()).isEqualTo(205001);
        assertThat(orderNotFound.required("data").isNull()).isTrue();
    }

    @Test
    void givenCFixtures_whenParse_thenUseRestTypesAndExcludeSensitiveFields() throws Exception {
        for (String fixture : REST_FIXTURES) {
            JsonNode root = readFixture(fixture);
            assertThat(root.required("code").isIntegralNumber()).as(fixture).isTrue();
            assertThat(root.required("message").isTextual()).as(fixture).isTrue();
            assertThat(root.required("traceId").asText()).as(fixture).matches("[0-9a-f]{32}");
            assertNoSensitiveFields(root.toString(), fixture);
        }

        List<ShowSummaryResponse> shows = readSuccessList("c/show-list-success.json", ShowSummaryResponse.class);
        assertThat(shows).hasSize(1);
        JsonNode show = readFixture("c/show-list-success.json").required("data").get(0);
        assertExactRecordFields(show, ShowSummaryResponse.class);
        assertTextId(show, "showId");
        assertTextId(show, "movieId");
        assertTextId(show, "cinemaId");
        assertAmount(show, "basePrice");
        assertThat(OffsetDateTime.parse(show.required("expiresAt").asText()))
                .isEqualTo(OffsetDateTime.parse(show.required("startTime").asText()));

        SeatMapResponse seatMapResponse = readSuccessData("c/seat-map-success.json", SeatMapResponse.class);
        assertThat(seatMapResponse.seats()).hasSize(2);
        JsonNode seatMap = readFixture("c/seat-map-success.json").required("data");
        assertExactRecordFields(seatMap, SeatMapResponse.class);
        assertExactRecordFields(seatMap.required("seats").get(0), SeatMapResponse.SeatItemResponse.class);
        assertThat(seatMap.required("seats").size()).isEqualTo(seatMap.required("seatCount").asInt());
        assertThat(seatMap.required("seats").get(0).required("status").asText()).isEqualTo("AVAILABLE");

        OrderResponse createdOrder = readSuccessData("c/create-order-success.json", OrderResponse.class);
        assertThat(createdOrder.orderNo()).isNotBlank();
        PageResult<OrderResponse> orderPage = readSuccessPage("c/order-page-success.json", OrderResponse.class);
        assertThat(orderPage.records()).hasSize(1);
        JsonNode order = readFixture("c/create-order-success.json").required("data");
        assertExactRecordFields(order, OrderResponse.class);
        JsonNode orderPageData = readFixture("c/order-page-success.json").required("data");
        assertExactRecordFields(orderPageData, PageResult.class);
        assertExactRecordFields(orderPageData.required("records").get(0), OrderResponse.class);
        assertTextId(order, "orderId");
        assertTextId(order, "showId");
        assertAmount(order, "unitPrice");
        assertAmount(order, "totalAmount");
        assertThat(order.required("status").asText()).isEqualTo("PENDING_PAYMENT");

        PaymentResponse paymentResponse = readSuccessData("c/payment-success.json", PaymentResponse.class);
        assertThat(paymentResponse.paymentNo()).isNotBlank();
        JsonNode payment = readFixture("c/payment-success.json").required("data");
        assertExactRecordFields(payment, PaymentResponse.class);
        assertThat(payment.required("orderStatus").asText()).isEqualTo("PAID");
        assertThat(payment.required("paymentStatus").asText()).isEqualTo("SUCCESS");
        assertTextId(payment, "ticketId");

        ElectronicTicketResponse ticketResponse = readSuccessData(
                "c/electronic-ticket-success.json", ElectronicTicketResponse.class);
        assertThat(ticketResponse.seatIds()).hasSize(2);
        JsonNode ticket = readFixture("c/electronic-ticket-success.json").required("data");
        assertExactRecordFields(ticket, ElectronicTicketResponse.class);
        assertThat(ticket.required("status").asText()).isEqualTo("VALID");
        assertThat(ticket.required("qrPayload").asText()).startsWith("cinewise:ticket:");

        RefundImpactResponse refundImpact = readSuccessData(
                "c/refund-impact-success.json", RefundImpactResponse.class);
        assertThat(refundImpact.impactText()).isNotBlank();
        RefundResponse refundResponse = readSuccessData("c/refund-success.json", RefundResponse.class);
        assertThat(refundResponse.refundNo()).isNotBlank();
        AlternativeShowsResponse alternatives = readSuccessData(
                "c/alternative-shows-success.json", AlternativeShowsResponse.class);
        assertThat(alternatives.shows()).hasSize(1);
        JsonNode refundImpactData = readFixture("c/refund-impact-success.json").required("data");
        assertExactRecordFields(refundImpactData, RefundImpactResponse.class);
        JsonNode refund = readFixture("c/refund-success.json").required("data");
        assertExactRecordFields(refund, RefundResponse.class);
        JsonNode alternativesData = readFixture("c/alternative-shows-success.json").required("data");
        assertExactRecordFields(alternativesData, AlternativeShowsResponse.class);
        assertExactRecordFields(alternativesData.required("shows").get(0), AlternativeShowResponse.class);
        assertAmount(refund, "refundAmount");
        assertThat(refund.required("refundStatus").asText()).isEqualTo("SUCCESS");
        assertThat(refund.required("orderStatus").asText()).isEqualTo("REFUNDED");
        assertThat(refund.required("ticketStatus").asText()).isEqualTo("REFUNDED");

        assertErrorFixture("c/seat-conflict-error.json", 204001);
        assertErrorFixture("c/idempotency-mismatch-error.json", 205005);
        assertErrorFixture("c/order-not-found-error.json", 205001);
        assertErrorFixture("c/unauthenticated-error.json", 100401);
    }

    @Test
    void givenTicketingOpenApi_whenRead_thenExposeSecurityIdempotencyAndRecoveryContracts() throws Exception {
        String openApiJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(openApiJson);

        assertCookieSecurity(openApi, "/api/v1/shows/{showId}/seats", "get");
        assertCookieSecurity(openApi, "/api/v1/orders", "post");
        assertCookieSecurity(openApi, "/api/v1/orders/{orderNo}/payments", "post");
        assertCookieSecurity(openApi, "/api/v1/orders/{orderNo}/refunds", "post");
        assertCookieSecurity(openApi, "/api/v1/tickets/{ticketId}", "get");

        assertRequiredHeader(openApi, "/api/v1/orders", "post", "Idempotency-Key");
        assertRequiredHeader(openApi, "/api/v1/orders/{orderNo}/cancel", "post", "Idempotency-Key");
        assertRequiredHeader(openApi, "/api/v1/orders/{orderNo}/payments", "post", "Idempotency-Key");
        assertRequiredHeader(openApi, "/api/v1/orders/{orderNo}/refunds", "post", "Idempotency-Key");
        assertThat(operation(openApi, "/api/v1/orders/{orderNo}/payments", "post").has("requestBody"))
                .isFalse();

        assertSchemaProperty(openApi, "ShowSummaryResponse", "showId", "string");
        assertSchemaProperty(openApi, "ShowSummaryResponse", "basePrice", "string");
        assertSchemaProperty(openApi, "OrderResponse", "orderId", "string");
        assertSchemaProperty(openApi, "OrderResponse", "totalAmount", "string");
        assertSchemaProperty(openApi, "PaymentResponse", "ticketId", "string");
        assertSchemaProperty(openApi, "ElectronicTicketResponse", "ticketId", "string");
        assertSchemaProperty(openApi, "RefundResponse", "refundAmount", "string");

        String normalizedOpenApi = openApiJson.toLowerCase();
        assertThat(normalizedOpenApi)
                .doesNotContain("paymentpassword")
                .doesNotContain("mockpassword")
                .doesNotContain("sixdigitpassword");
    }

    private JsonNode readFixture(String relativePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(FIXTURE_ROOT + relativePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    /**
     * 以运行时 ObjectMapper 反序列化成功夹具，防止 DTO 改名、类型变化或夹具出现未声明字段时静默通过。
     */
    private <T> T readSuccessData(String relativePath, Class<T> responseType) throws IOException {
        return objectMapper.treeToValue(readFixture(relativePath).required("data"), responseType);
    }

    /** 列表夹具同样必须按实际元素 DTO 解析，不能只检查第一条的少量字段。 */
    private <T> List<T> readSuccessList(String relativePath, Class<T> elementType) throws IOException {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.readerFor(listType)
                .readValue(readFixture(relativePath).required("data").traverse(objectMapper));
    }

    /** 分页夹具以实际 PageResult<T> 解析，确保分页包装和记录 DTO 同时受保护。 */
    private <T> PageResult<T> readSuccessPage(String relativePath, Class<T> elementType) throws IOException {
        JavaType pageType = objectMapper.getTypeFactory().constructParametricType(PageResult.class, elementType);
        return objectMapper.readerFor(pageType)
                .readValue(readFixture(relativePath).required("data").traverse(objectMapper));
    }

    /**
     * 反序列化会拒绝改名或多余字段；此断言补充拒绝遗漏新增 DTO 字段，保持每份夹具字段完整。
     */
    private void assertExactRecordFields(JsonNode node, Class<?> recordType) {
        Set<String> actualFields = new HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        Set<String> expectedFields = new HashSet<>();
        for (java.lang.reflect.RecordComponent component : recordType.getRecordComponents()) {
            expectedFields.add(component.getName());
        }
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    private void assertToolEnvelope(JsonNode root) {
        Set<String> actualFields = new HashSet<>();
        root.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(TOOL_RESULT_FIELDS);
        assertThat(root.required("status").asText()).isIn("SUCCESS", "FAILED", "PROCESSING");
        assertNoSensitiveFields(root.toString(), "B ToolResult");
    }

    private void assertErrorFixture(String relativePath, int expectedCode) throws IOException {
        JsonNode root = readFixture(relativePath);
        assertThat(root.required("code").asInt()).isEqualTo(expectedCode);
        assertThat(root.required("data").isNull()).isTrue();
    }

    private void assertTextId(JsonNode node, String fieldName) {
        assertThat(node.required(fieldName).isTextual()).isTrue();
        assertThat(node.required(fieldName).asText()).matches("\\d+");
    }

    private void assertAmount(JsonNode node, String fieldName) {
        assertThat(node.required(fieldName).isTextual()).isTrue();
        assertThat(node.required(fieldName).asText()).matches("\\d+\\.\\d{2}");
    }

    private void assertNoSensitiveFields(String json, String fixtureName) {
        String normalized = json.toLowerCase();
        assertThat(normalized)
                .as(fixtureName)
                .doesNotContain("paymentpassword")
                .doesNotContain("mockpassword")
                .doesNotContain("jwt")
                .doesNotContain("cookie")
                .doesNotContain("userid")
                .doesNotContain("actionid");
    }

    private void assertCookieSecurity(JsonNode openApi, String path, String method) {
        JsonNode security = operation(openApi, path, method).required("security");
        assertThat(security.isArray()).isTrue();
        assertThat(security.get(0).required("cookieAuth").isArray()).isTrue();
    }

    private void assertRequiredHeader(JsonNode openApi, String path, String method, String headerName) {
        JsonNode parameters = operation(openApi, path, method).required("parameters");
        boolean headerFound = false;
        for (JsonNode parameter : parameters) {
            if (headerName.equals(parameter.path("name").asText())
                    && "header".equals(parameter.path("in").asText())
                    && parameter.path("required").asBoolean()) {
                headerFound = true;
                break;
            }
        }
        assertThat(headerFound).as("%s %s should require %s", method, path, headerName).isTrue();
    }

    private void assertSchemaProperty(JsonNode openApi, String schemaName, String propertyName, String type) {
        JsonNode property = openApi.required("components")
                .required("schemas")
                .required(schemaName)
                .required("properties")
                .required(propertyName);
        JsonNode declaredType = property.required("type");
        if (declaredType.isArray()) {
            assertThat(declaredType).anySatisfy(candidate -> assertThat(candidate.asText()).isEqualTo(type));
            return;
        }
        assertThat(declaredType.asText()).isEqualTo(type);
    }

    private JsonNode operation(JsonNode openApi, String path, String method) {
        return openApi.required("paths").required(path).required(method);
    }
}
