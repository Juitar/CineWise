package com.miaoyu.ticket.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.PageResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderContractFixtureTest {

    private static final String ROOT = "fixtures/ticketing/c/";
    private static final Set<String> FORBIDDEN_ADMIN_FIELDS = Set.of(
            "email",
            "passwordHash",
            "tokenVersion",
            "clientRequestId",
            "idempotencyKey",
            "qrPayload",
            "impactSnapshot",
            "actionId",
            "loginLog");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenAdminFixtures_whenDeserialize_thenMatchPublicDtoAndExcludeSecrets() throws Exception {
        JsonNode pageRoot = read("admin-order-page-success.json");
        JavaType pageType = objectMapper.getTypeFactory()
                .constructParametricType(PageResult.class, AdminOrderSummaryResponse.class);
        PageResult<AdminOrderSummaryResponse> page = objectMapper.readerFor(pageType)
                .readValue(pageRoot.required("data").traverse(objectMapper));
        assertThat(page.records()).singleElement().satisfies(order -> {
            assertThat(order.orderId()).isEqualTo("3002");
            assertThat(order.totalAmount()).isEqualTo("68.00");
            assertThat(order.emailMasked()).isEqualTo("r***@example.com");
        });

        JsonNode detailRoot = read("admin-order-detail-success.json");
        AdminOrderDetailResponse detail = objectMapper.treeToValue(
                detailRoot.required("data"), AdminOrderDetailResponse.class);
        assertThat(detail.seats()).singleElement().satisfies(seat -> assertThat(seat.seatId())
                .isEqualTo("4002"));
        assertThat(detail.payment().status()).isEqualTo("SUCCESS");
        assertThat(detail.ticket().status()).isEqualTo("REFUNDED");
        assertThat(detail.refund().status()).isEqualTo("SUCCESS");

        assertNoForbiddenFields(pageRoot);
        assertNoForbiddenFields(detailRoot);
        assertError("admin-user-query-too-broad-error.json", 201010);
        assertError("admin-user-directory-unavailable-error.json", 301002);
    }

    @Test
    void givenOpenApi_whenReadAdminSchemas_thenExposeOnlyReadContract() throws Exception {
        JsonNode openApi = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(openApi.required("paths").has("/api/v1/admin/orders")).isTrue();
        assertThat(openApi.required("paths").has("/api/v1/admin/orders/{orderNo}")).isTrue();
        assertSchemaHasNoForbiddenFields(openApi, "AdminOrderSummaryResponse");
        assertSchemaHasNoForbiddenFields(openApi, "AdminOrderDetailResponse");
        assertSchemaHasNoForbiddenFields(openApi, "AdminPaymentResponse");
        assertSchemaHasNoForbiddenFields(openApi, "AdminTicketResponse");
        assertSchemaHasNoForbiddenFields(openApi, "AdminRefundResponse");
    }

    private JsonNode read(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource(ROOT + name);
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private void assertError(String name, int code) throws IOException {
        JsonNode root = read(name);
        assertThat(root.required("code").asInt()).isEqualTo(code);
        assertThat(root.required("data").isNull()).isTrue();
        assertNoForbiddenFields(root);
    }

    private void assertNoForbiddenFields(JsonNode root) {
        String json = root.toString();
        FORBIDDEN_ADMIN_FIELDS.forEach(field -> assertThat(json).doesNotContain("\"" + field + "\""));
    }

    private void assertSchemaHasNoForbiddenFields(JsonNode openApi, String schemaName) {
        JsonNode properties = openApi.required("components")
                .required("schemas")
                .required(schemaName)
                .required("properties");
        FORBIDDEN_ADMIN_FIELDS.forEach(field -> assertThat(properties.has(field)).isFalse());
    }
}
