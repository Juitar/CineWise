package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.TextReplyFacts;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 使用本人会话中最新的持久化 PLAN_CARD 解释方案，不重新调用模型或推荐 Tool。 */
@Service
public class AgentPlanCardFollowUpResolver {
    private static final Pattern PLAN_REFERENCE = Pattern.compile(
            "第\\s*(?<index>[1-9]\\d{0,2}|[一二两三四五六七八九十]{1,3})\\s*个方案");
    private static final Pattern EXPLANATION_REQUEST = Pattern.compile("解释|说明|为什么|调整|合适|怎么样");
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm")
            .withZone(ClockConfiguration.BUSINESS_ZONE_ID);
    private static final int MAX_DISPLAY_LENGTH = 80;
    private static final int MAX_REASON_COUNT = 3;

    private final AgentMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public AgentPlanCardFollowUpResolver(AgentMessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    /** 非方案解释请求返回空；命中后无论卡片是否存在都返回安全 TEXT，避免误走推荐流程。 */
    public Optional<ReplyGenerationResponse> resolve(long sessionId, long userId, String input) {
        // 只解析“第几个方案”的解释请求；普通聊天必须继续走正常意图识别。
        Integer requestedIndex = referencedPlanIndex(input);
        if (requestedIndex == null) {
            return Optional.empty();
        }
        var latest = messageRepository.findLatestPlanCardBySessionIdAndUserId(sessionId, userId);
        // 查询携带 userId，不能从相同 sessionId 的其他账号读取方案卡。
        if (latest.isEmpty() || latest.get().payload() == null) {
            return Optional.of(text("当前会话里没有可解释的推荐方案，请先生成一组推荐。"));
        }
        JsonNode payload = readPayload(latest.get().payload().value());
        JsonNode plans = payload.path("plans");
        if (!plans.isArray()) {
            return Optional.of(text("最新推荐卡片暂时无法读取，请重新生成推荐。"));
        }
        if (requestedIndex > plans.size()) {
            String available = plans.isEmpty() ? "没有可选方案" : "只有 " + plans.size() + " 个方案";
            return Optional.of(text("最新推荐" + available + "，请重新选择。"));
        }
        return Optional.of(text(explain(requestedIndex, plans.get(requestedIndex - 1), payload)));
    }

    private static Integer referencedPlanIndex(String input) {
        // 先要求存在解释语义再匹配序号，避免用户单独提到“第一个”时误进入该分支。
        if (input == null || input.isBlank() || !EXPLANATION_REQUEST.matcher(input).find()) {
            return null;
        }
        java.util.regex.Matcher matcher = PLAN_REFERENCE.matcher(input);
        if (!matcher.find()) {
            return null;
        }
        return parsePositiveNumber(matcher.group("index"));
    }

    private static Integer parsePositiveNumber(String value) {
        // 支持常用阿拉伯数字和中文十以内/十位表达，不接受模糊或负数索引。
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.valueOf(value);
        }
        if ("十".equals(value)) {
            return 10;
        }
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : chineseDigit(value.charAt(0));
            int units = ten == value.length() - 1 ? 0 : chineseDigit(value.charAt(ten + 1));
            return tens < 0 || units < 0 ? null : tens * 10 + units;
        }
        int digit = value.length() == 1 ? chineseDigit(value.charAt(0)) : -1;
        return digit < 0 ? null : digit;
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private JsonNode readPayload(String json) {
        // 历史数据可能经历过 JSON 二次编码；读取失败返回空对象，不能抛异常中断整次会话。
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.isTextual() ? objectMapper.readTree(root.asText()) : root;
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static String explain(int index, JsonNode plan, JsonNode card) {
        // 说明文本只引用卡片中已有公开字段，不重新调用模型、推荐服务或读取私有上下文。
        List<String> facts = new ArrayList<>();
        addFact(facts, "影片", displayText(plan, "movieName"));
        addFact(facts, "影院", displayText(plan, "cinemaName"));
        addFact(facts, "开场", displayInstant(plan, "startTime"));
        addFact(facts, "票价", price(plan));
        addFact(facts, "评分", displayText(plan, "rating"));
        if (plan.path("distanceMeters").canConvertToInt()) {
            facts.add("距离约 " + plan.path("distanceMeters").asInt() + " 米");
        }
        List<String> reasons = reasons(plan.path("reasons"));
        StringBuilder reply = new StringBuilder("第").append(index).append("个方案");
        if (!facts.isEmpty()) {
            reply.append("：").append(String.join("，", facts)).append("。");
        } else {
            reply.append("的公开信息不完整。");
        }
        if (!reasons.isEmpty()) {
            reply.append("推荐原因：").append(String.join("；", reasons)).append("。");
        }
        boolean shouldAdjust = card.path("degraded").asBoolean(false)
                || card.path("expired").asBoolean(false)
                || plan.path("expired").asBoolean(false)
                || !plan.path("purchaseEligible").asBoolean(false);
        // 降级、过期或不可购票的方案只能建议重算，不能把过期信息包装成可下单建议。
        reply.append(shouldAdjust
                ? "这个方案需要继续调整或重新生成后再决定。"
                : "当前信息下不必继续调整；如果时间、影院或预算不合适，再修改条件即可。");
        return reply.toString();
    }

    private static String displayInstant(JsonNode node, String field) {
        // 时间仅接受标准 Instant；异常格式按缺失字段处理而不是猜测时区。
        String value = displayText(node, field);
        if (value == null) {
            return null;
        }
        try {
            return START_TIME.format(Instant.parse(value));
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private static String price(JsonNode plan) {
        String value = displayText(plan, "price");
        if (value == null) {
            return null;
        }
        String currency = displayText(plan, "currency");
        return value + ("CNY".equals(currency) ? " 元" : currency == null ? "" : " " + currency);
    }

    private static List<String> reasons(JsonNode reasons) {
        // 推荐原因限制数量和文本长度，防止历史载荷在普通文本回复中无限膨胀。
        if (!reasons.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode reason : reasons) {
            String value = safeDisplayText(reason.isTextual() ? reason.asText() : null);
            if (value != null) {
                values.add(value);
            }
            if (values.size() == MAX_REASON_COUNT) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private static void addFact(List<String> facts, String label, String value) {
        if (value != null) {
            facts.add(label + " " + value);
        }
    }

    private static String displayText(JsonNode node, String field) {
        return node.path(field).isTextual() ? safeDisplayText(node.path(field).asText()) : null;
    }

    private static String safeDisplayText(String value) {
        // 清理控制字符并截断展示文本，避免卡片载荷直接影响聊天布局或日志可读性。
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        return normalized.length() <= MAX_DISPLAY_LENGTH
                ? normalized : normalized.substring(0, MAX_DISPLAY_LENGTH);
    }

    private static ReplyGenerationResponse text(String value) {
        return new ReplyGenerationResponse(value, AgentReplyMessageType.TEXT, new TextReplyFacts());
    }
}
