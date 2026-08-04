package com.miaoyu.ticket.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.ticketing.api.AvailableDatesResponse;
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
    private static final Set<String> RESULT_FIELDS = Set.of("code", "message", "data", "traceId");
    private static final Set<String> B_SHOW_FIELDS = Set.of(
            "showId",
            "movieId",
            "cinemaId",
            "startTime",
            "languageVersion",
            "basePrice",
            "status");
    private static final Set<String> SHOW_SUMMARY_FIELDS = Set.of(
            "showId",
            "movieId",
            "cinemaId",
            "cinemaName",
            "auditoriumId",
            "auditoriumName",
            "startTime",
            "endTime",
            "expiresAt",
            "languageVersion",
            "basePrice",
            "availableSeatCount",
            "status",
            "dataType",
            "stateVersion",
            "updatedAt");
    private static final Set<String> AVAILABLE_DATES_FIELDS = Set.of("dates");
    private static final Set<String> AVAILABLE_DATE_FIELDS = Set.of("date", "showCount");
    private static final Set<String> SEAT_MAP_FIELDS = Set.of(
            "showId",
            "auditoriumId",
            "auditoriumName",
            "rowCount",
            "seatCount",
            "availableSeatCount",
            "stateVersion",
            "updatedAt",
            "seats");
    private static final Set<String> SEAT_FIELDS = Set.of(
            "seatId", "rowNo", "seatNo", "seatLabel", "status", "stateVersion");
    private static final Set<String> PAGE_FIELDS = Set.of("total", "page", "size", "records");
    private static final Set<String> ORDER_FIELDS = Set.of(
            "orderId",
            "orderNo",
            "showId",
            "seatIds",
            "ticketCount",
            "unitPrice",
            "totalAmount",
            "status",
            "expireTime",
            "stateVersion",
            "updatedAt");
    private static final Set<String> ALTERNATIVE_SHOWS_FIELDS = Set.of("orderNo", "shows");
    private static final Set<String> ALTERNATIVE_SHOW_FIELDS = Set.of(
            "showId", "movieId", "cinemaId", "startTime", "basePrice", "status", "availableSeatCount");
    private static final List<String> REST_FIXTURES = List.of(
            "c/available-dates-success.json",
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
        JsonNode queryShowsData = queryShows.required("data");
        assertExactFields(queryShowsData, "B queryShows data", Set.of("shows"));
        JsonNode shows = queryShowsData.required("shows");
        assertThat(shows.isArray()).isTrue();
        assertThat(shows).isNotEmpty();
        Instant dataAt = Instant.parse(queryShows.required("dataAt").asText());
        Instant expiresAt = Instant.parse(queryShows.required("expiresAt").asText());
        assertThat(expiresAt).isAfter(dataAt);
        for (JsonNode show : shows) {
            assertExactFields(show, "B queryShows record", B_SHOW_FIELDS);
            assertTextId(show, "showId");
            assertTextId(show, "movieId");
            assertTextId(show, "cinemaId");
            assertAmount(show, "basePrice");
            Instant showStartTime = Instant.parse(show.required("startTime").asText());
            assertThat(expiresAt).isBeforeOrEqualTo(showStartTime);
        }

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
            assertExactFields(root, fixture, RESULT_FIELDS);
            assertThat(root.required("code").isIntegralNumber()).as(fixture).isTrue();
            assertThat(root.required("message").isTextual()).as(fixture).isTrue();
            assertThat(root.required("traceId").asText()).as(fixture).matches("[0-9a-f]{32}");
            assertNoSensitiveFields(root.toString(), fixture);
        }

        AvailableDatesResponse availableDates = readSuccessData(
                "c/available-dates-success.json", AvailableDatesResponse.class);
        JsonNode availableDatesData = readFixture("c/available-dates-success.json").required("data");
        assertExactFields(availableDatesData, "C available-dates data", AVAILABLE_DATES_FIELDS);
        assertExactRecordFields(availableDatesData, AvailableDatesResponse.class);
        assertThat(availableDates.dates()).hasSize(2);
        for (JsonNode availableDate : availableDatesData.required("dates")) {
            assertExactFields(availableDate, "C available-date record", AVAILABLE_DATE_FIELDS);
            assertExactRecordFields(availableDate, AvailableDatesResponse.AvailableDateItemResponse.class);
            assertThat(availableDate.required("showCount").asInt()).isPositive();
            java.time.LocalDate.parse(availableDate.required("date").asText());
        }

        List<ShowSummaryResponse> showResponses =
                readSuccessList("c/show-list-success.json", ShowSummaryResponse.class);
        JsonNode showList = readFixture("c/show-list-success.json").required("data");
        assertThat(showList.isArray()).isTrue();
        assertThat(showList).isNotEmpty();
        assertThat(showResponses).hasSize(showList.size());
        for (JsonNode show : showList) {
            assertExactFields(show, "C show-list record", SHOW_SUMMARY_FIELDS);
            assertExactRecordFields(show, ShowSummaryResponse.class);
            assertTextId(show, "showId");
            assertTextId(show, "movieId");
            assertTextId(show, "cinemaId");
            assertTextId(show, "auditoriumId");
            assertAmount(show, "basePrice");
            assertThat(OffsetDateTime.parse(show.required("expiresAt").asText()))
                    .isEqualTo(OffsetDateTime.parse(show.required("startTime").asText()));
        }

        SeatMapResponse seatMapResponse = readSuccessData("c/seat-map-success.json", SeatMapResponse.class);
        assertThat(seatMapResponse.seats()).hasSize(2);
        JsonNode seatMap = readFixture("c/seat-map-success.json").required("data");
        assertExactFields(seatMap, "C seat-map data", SEAT_MAP_FIELDS);
        assertExactRecordFields(seatMap, SeatMapResponse.class);
        assertThat(seatMap.required("seats").size()).isEqualTo(seatMap.required("seatCount").asInt());
        for (JsonNode seat : seatMap.required("seats")) {
            assertExactFields(seat, "C seat-map record", SEAT_FIELDS);
            assertExactRecordFields(seat, SeatMapResponse.SeatItemResponse.class);
            assertTextId(seat, "seatId");
            assertThat(seat.required("status").asText()).isIn("AVAILABLE", "LOCKED", "SOLD", "UNAVAILABLE");
        }

        PageResult<OrderResponse> orderPageResponse =
                readSuccessPage("c/order-page-success.json", OrderResponse.class);
        JsonNode orderPageData = readFixture("c/order-page-success.json").required("data");
        assertExactFields(orderPageData, "C order-page data", PAGE_FIELDS);
        assertExactRecordFields(orderPageData, PageResult.class);
        JsonNode orderRecords = orderPageData.required("records");
        assertThat(orderRecords.isArray()).isTrue();
        assertThat(orderRecords).isNotEmpty();
        assertThat(orderRecords.size()).isEqualTo(orderPageData.required("total").asInt());
        assertThat(orderPageResponse.records()).hasSize(orderRecords.size());
        for (JsonNode orderRecord : orderRecords) {
            assertOrderFields(orderRecord, "C order-page record");
            assertExactRecordFields(orderRecord, OrderResponse.class);
        }

        AlternativeShowsResponse alternativeShowsResponse = readSuccessData(
                "c/alternative-shows-success.json", AlternativeShowsResponse.class);
        JsonNode alternativeShows = readFixture("c/alternative-shows-success.json").required("data");
        assertExactFields(alternativeShows, "C alternative-shows data", ALTERNATIVE_SHOWS_FIELDS);
        assertExactRecordFields(alternativeShows, AlternativeShowsResponse.class);
        JsonNode alternativeShowRecords = alternativeShows.required("shows");
        assertThat(alternativeShowRecords.isArray()).isTrue();
        assertThat(alternativeShowRecords).isNotEmpty();
        assertThat(alternativeShowsResponse.shows()).hasSize(alternativeShowRecords.size());
        for (JsonNode alternativeShow : alternativeShowRecords) {
            assertExactFields(alternativeShow, "C alternative-show record", ALTERNATIVE_SHOW_FIELDS);
            assertExactRecordFields(alternativeShow, AlternativeShowResponse.class);
            assertTextId(alternativeShow, "showId");
            assertTextId(alternativeShow, "movieId");
            assertTextId(alternativeShow, "cinemaId");
            assertAmount(alternativeShow, "basePrice");
            OffsetDateTime.parse(alternativeShow.required("startTime").asText());
        }

        OrderResponse createdOrder = readSuccessData("c/create-order-success.json", OrderResponse.class);
        assertThat(createdOrder.orderNo()).isNotBlank();
        JsonNode order = readFixture("c/create-order-success.json").required("data");
        assertOrderFields(order, "C create-order data");
        assertExactRecordFields(order, OrderResponse.class);
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
        JsonNode refundImpactData = readFixture("c/refund-impact-success.json").required("data");
        assertExactRecordFields(refundImpactData, RefundImpactResponse.class);
        JsonNode refund = readFixture("c/refund-success.json").required("data");
        assertExactRecordFields(refund, RefundResponse.class);
        JsonNode alternativesData = readFixture("c/alternative-shows-success.json").required("data");
        assertExactRecordFields(alternativesData, AlternativeShowsResponse.class);
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
        assertSchemaProperty(openApi, "AvailableDateItemResponse", "date", "string");
        assertSchemaProperty(openApi, "AvailableDateItemResponse", "showCount", "integer");
        assertSchemaProperty(openApi, "ShowSummaryResponse", "basePrice", "string");
        assertSchemaProperty(openApi, "OrderResponse", "orderId", "string");
        assertSchemaProperty(openApi, "OrderResponse", "totalAmount", "string");
        assertSchemaProperty(openApi, "PaymentResponse", "ticketId", "string");
        assertSchemaProperty(openApi, "ElectronicTicketResponse", "ticketId", "string");
        assertSchemaProperty(openApi, "RefundResponse", "refundAmount", "string");
        assertSchemaProperty(openApi, "AlternativeShowResponse", "movieId", "string");

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
        assertExactFields(root, "B ToolResult", TOOL_RESULT_FIELDS);
        assertThat(root.required("status").asText()).isIn("SUCCESS", "FAILED", "PROCESSING");
        assertNoSensitiveFields(root.toString(), "B ToolResult");
    }

    private void assertOrderFields(JsonNode order, String fixtureName) {
        assertExactFields(order, fixtureName, ORDER_FIELDS);
        assertTextId(order, "orderId");
        assertTextId(order, "showId");
        assertAmount(order, "unitPrice");
        assertAmount(order, "totalAmount");
        OffsetDateTime.parse(order.required("expireTime").asText());
        OffsetDateTime.parse(order.required("updatedAt").asText());
    }

    private void assertExactFields(JsonNode node, String fixtureName, Set<String> expectedFields) {
        Set<String> actualFields = new HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).as(fixtureName).containsExactlyInAnyOrderElementsOf(expectedFields);
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
