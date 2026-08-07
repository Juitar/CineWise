package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.AgentIntent;
import com.miaoyu.ticket.agent.application.model.IntentClassificationRequest;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.infrastructure.model.DeepSeekModelGateway;
import com.miaoyu.ticket.agent.infrastructure.model.DeepSeekProperties;
import com.miaoyu.ticket.agent.infrastructure.model.AgentModelGatewayException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** 验证真实适配器使用 OpenAI 兼容请求格式，测试服务器不记录或输出密钥。 */
class DeepSeekModelGatewayTest {

    @Test
    void shouldCallChatCompletionsWithConfiguredModelAndBearerHeader() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ObjectMapper responseMapper = new ObjectMapper();
            var responseJson = responseMapper.createObjectNode();
            responseJson.putArray("choices").addObject().putObject("message")
                    .put("content", "{\"planId\":\"p-1\",\"version\":1,\"nodes\":[]}");
            byte[] response = responseMapper.writeValueAsBytes(responseJson);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            DeepSeekProperties properties = new DeepSeekProperties(
                    true, "test-key", "deepseek-v4-flash", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            DeepSeekModelGateway gateway = new DeepSeekModelGateway(
                    RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper(),
                    new PlanSchemaValidator(new ToolRegistry(Set.of())));

            var result = gateway.generatePlan(new PlanGenerationRequest(
                    "request-1", "找电影 user@example.com Bearer secret-token", Map.of(), Set.of()));

            assertThat(result.candidatePlan().planId()).isEqualTo("p-1");
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(body.get()).contains("deepseek-v4-flash").contains("json_object");
            assertThat(body.get()).doesNotContain("user@example.com").doesNotContain("secret-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldHideProviderFailureAndInvalidJson() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            DeepSeekProperties properties = new DeepSeekProperties(
                    true, "private-key", "deepseek-v4-flash", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            DeepSeekModelGateway gateway = new DeepSeekModelGateway(
                    RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper(),
                    new PlanSchemaValidator(new ToolRegistry(Set.of())));

            assertThatThrownBy(() -> gateway.generatePlan(new PlanGenerationRequest(
                    "request-2", "找电影", Map.of(), Set.of())))
                    .isInstanceOf(AgentModelGatewayException.class)
                    .hasMessageNotContaining("private-key")
                    .hasMessageNotContaining("not-json");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDowngradeInvalidOrUnknownIntentToGeneralChat() throws IOException {
        AtomicReference<String> content = new AtomicReference<>("not-json");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = ("{\"choices\":[{\"message\":{\"content\":\"" + content.get()
                    + "\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            DeepSeekProperties properties = new DeepSeekProperties(
                    true, "test-key", "deepseek-v4-flash", "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2));
            DeepSeekModelGateway gateway = new DeepSeekModelGateway(
                    RestClient.builder().baseUrl(properties.baseUrl()).build(), properties, new ObjectMapper(),
                    new PlanSchemaValidator(new ToolRegistry(Set.of())));

            assertThat(gateway.classifyIntent(new IntentClassificationRequest("这啥")))
                    .isEqualTo(AgentIntent.GENERAL_CHAT);
            content.set("{\\\"intent\\\":\\\"UNKNOWN\\\"}");
            assertThat(gateway.classifyIntent(new IntentClassificationRequest("你好")))
                    .isEqualTo(AgentIntent.GENERAL_CHAT);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldAllowBlankKeyWhenMockIsEnabledButRejectItForRealGateway() {
        DeepSeekProperties mockProperties = new DeepSeekProperties(
                false, "", "deepseek-v4-flash", "https://api.deepseek.com", Duration.ofSeconds(2));

        assertThatThrownBy(mockProperties::requireEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未开启 DeepSeek");
        DeepSeekProperties enabledProperties = new DeepSeekProperties(
                true, "", "deepseek-v4-flash", "https://api.deepseek.com", Duration.ofSeconds(2));
        assertThatThrownBy(enabledProperties::requireEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }
}
