package com.miaoyu.ticket.agent.infrastructure.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.AgentIntent;
import com.miaoyu.ticket.agent.application.model.IntentClassificationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** DeepSeek OpenAI 兼容接口的最小适配器，供应商 JSON 不离开此类。 */
public final class DeepSeekModelGateway implements ModelGateway {
    private static final String PLAN_SYSTEM_PROMPT = """
            你是 CineWise Agent 计划器。只输出 JSON 对象，不得输出 Markdown。
            根字段为 planId、version、nodes。每个节点为 nodeId、type、targetName、inputRefs、dependsOn、failurePolicy。
            ASK_USER 节点的 targetName 必须是需要补充字段的允许 Tool 名称。
            inputRefs 每项为 inputName、source、sourceId；只能引用已给出的 slots，不能填写业务值。
            只能使用 allowTools 中的工具，写工具必须依赖 CONFIRM_ACTION 节点。
            """;
    private static final String REPLY_SYSTEM_PROMPT = """
            你是 CineWise Agent 回复生成器。只输出 JSON 对象，字段为 text、messageType。
            只能依据提供的已校验 facts 润色文案，不能新增业务事实、工具、订单或座位。
            """;
    private static final String INTENT_SYSTEM_PROMPT = """
            你是 CineWise Agent 意图分类器。只输出 JSON 对象，字段为 intent。
            intent 只能是 MOVIE、TRAVEL、GENERAL_CHAT。找电影、推荐、日期或场次为 MOVIE；
            已有出行任务的天气或出行建议为 TRAVEL；问候、页面解释、闲聊和不确定输入为 GENERAL_CHAT。
            不得输出工具名、参数、ID 或解释。
            """;

    private final RestClient restClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final PlanSchemaValidator validator;

    public DeepSeekModelGateway(
            RestClient restClient,
            DeepSeekProperties properties,
            ObjectMapper objectMapper,
            PlanSchemaValidator validator) {
        this.restClient = Objects.requireNonNull(restClient, "restClient 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.validator = Objects.requireNonNull(validator, "validator 不能为空");
    }

    @Override
    public AgentIntent classifyIntent(IntentClassificationRequest request) {
        try {
            JsonNode content = complete(INTENT_SYSTEM_PROMPT,
                    object("input", PromptSanitizer.sanitize(Objects.requireNonNull(request, "request 不能为空").input())));
            return AgentIntent.valueOf(requiredText(content, "intent"));
        } catch (RuntimeException exception) {
            // 意图不确定时不允许模型扩大工具范围。
            return AgentIntent.GENERAL_CHAT;
        }
    }

    @Override
    public PlanGenerationResponse generatePlan(PlanGenerationRequest request) {
        PlanGenerationRequest planRequest = Objects.requireNonNull(request, "request 不能为空");
        JsonNode content = complete(PLAN_SYSTEM_PROMPT, object("input", PromptSanitizer.sanitize(planRequest.input()),
                "slots", PromptSanitizer.sanitizeSlots(planRequest.confirmedSlots()),
                "allowTools", planRequest.allowedToolNames(),
                "profileTags", planRequest.profileTags().stream()
                        .map(tag -> Map.of(
                                "type", PromptSanitizer.sanitize(tag.type()),
                                "value", PromptSanitizer.sanitize(tag.value()),
                                "polarity", PromptSanitizer.sanitize(tag.polarity()),
                                "weight", PromptSanitizer.sanitize(tag.weight()),
                                "confidence", PromptSanitizer.sanitize(tag.confidence()),
                                "source", PromptSanitizer.sanitize(tag.source()),
                                "updatedAt", PromptSanitizer.sanitize(tag.updatedAt())))
                        .toList()));
        CandidatePlan plan = parsePlan(content);
        PlanValidationContext context = new PlanValidationContext(
                planRequest.confirmedSlots().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, ignored -> String.class)),
                Map.of(), new SlotSnapshot(0L, planRequest.confirmedSlots()));
        return new PlanGenerationResponse(plan, validator.validate(plan, context));
    }

    @Override
    public ReplyGenerationResponse generateReply(ReplyGenerationRequest request) {
        ReplyGenerationRequest replyRequest = Objects.requireNonNull(request, "request 不能为空");
        JsonNode content = complete(REPLY_SYSTEM_PROMPT, object("input", PromptSanitizer.sanitize(replyRequest.input()),
                "messageType", replyRequest.requestedType().name(), "facts", replyRequest.payload()));
        String text = requiredText(content, "text");
        String messageType = requiredText(content, "messageType");
        if (!replyRequest.requestedType().name().equals(messageType)) {
            throw new AgentModelGatewayException("模型回复类型不匹配");
        }
        return new ReplyGenerationResponse(text, replyRequest.requestedType(), replyRequest.payload());
    }

    private JsonNode complete(String systemPrompt, Object input) {
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.model(),
                    "stream", false,
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", objectMapper.writeValueAsString(input))));
            JsonNode response = restClient.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body).retrieve().body(JsonNode.class);
            if (response == null) {
                throw new AgentModelGatewayException("模型响应为空");
            }
            return objectMapper.readTree(requiredText(response.path("choices").path(0).path("message"), "content"));
        } catch (RestClientException | JsonProcessingException exception) {
            throw new AgentModelGatewayException("模型调用失败", exception);
        }
    }

    private CandidatePlan parsePlan(JsonNode content) {
        try {
            String planId = requiredText(content, "planId");
            int version = content.path("version").asInt(0);
            if (!content.path("nodes").isArray()) {
                throw new AgentModelGatewayException("模型计划 nodes 无效");
            }
            List<CandidatePlanNode> nodes = new ArrayList<>();
            for (JsonNode node : content.path("nodes")) {
                List<InputReference> refs = new ArrayList<>();
                for (JsonNode ref : node.path("inputRefs")) {
                    refs.add(new InputReference(requiredText(ref, "inputName"),
                            InputReferenceSource.valueOf(requiredText(ref, "source")), requiredText(ref, "sourceId")));
                }
                List<String> dependencies = new ArrayList<>();
                for (JsonNode dependency : node.path("dependsOn")) {
                    dependencies.add(dependency.asText());
                }
                nodes.add(new CandidatePlanNode(requiredText(node, "nodeId"),
                        PlanNodeType.valueOf(requiredText(node, "type")), nullableText(node, "targetName"), refs,
                        dependencies, FailurePolicy.valueOf(requiredText(node, "failurePolicy"))));
            }
            return new CandidatePlan(planId, version, nodes);
        } catch (IllegalArgumentException exception) {
            throw new AgentModelGatewayException("模型计划结构无效", exception);
        }
    }

    private Object object(Object... values) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(valuesToMap(values)), Object.class);
        } catch (JsonProcessingException exception) {
            throw new AgentModelGatewayException("模型请求构造失败", exception);
        }
    }

    private static Map<String, Object> valuesToMap(Object[] values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("请求字段必须成对出现");
        }
        java.util.LinkedHashMap<String, Object> mapped = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            mapped.put((String) values[index], values[index + 1]);
        }
        return Map.copyOf(mapped);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new AgentModelGatewayException("模型响应缺少 " + field);
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : requiredText(node, field);
    }
}
