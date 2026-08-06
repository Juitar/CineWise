package com.miaoyu.ticket.agent.application.confirmation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 仅把已校验计划快照转换为既有建单确认命令；不创建 action 或写标识。 */
public final class CreateOrderConfirmationCommandFactory {
    private final ObjectMapper objectMapper;

    public CreateOrderConfirmationCommandFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    public ConfirmedOrderCommand create(ExecutionPlanNode node) {
        if (node == null || !CreateOrderTool.TARGET_NAME.equals(node.targetName())) {
            throw invalidParameter();
        }
        Map<String, String> values = node.slotSnapshot().values();
        String showId = slotValue(node, values, "showId");
        String seatIdsJson = slotValue(node, values, "seatIds");
        List<String> seatIds = parseSeatIds(seatIdsJson);
        return new ConfirmedOrderCommand(CreateOrderTool.TARGET_NAME, showId, seatIds);
    }

    private static String slotValue(ExecutionPlanNode node, Map<String, String> values, String inputName) {
        InputReference reference = node.inputRefs().stream()
                .filter(item -> inputName.equals(item.inputName()))
                .findFirst().orElseThrow(CreateOrderConfirmationCommandFactory::invalidParameter);
        if (reference.source() != InputReferenceSource.SLOT || reference.sourceId() == null) {
            throw invalidParameter();
        }
        String value = values.get(reference.sourceId());
        if (value == null || value.isBlank()) {
            throw invalidParameter();
        }
        return value;
    }

    private List<String> parseSeatIds(String json) {
        try {
            JsonNode array = objectMapper.readTree(json);
            if (!array.isArray() || array.size() < 1 || array.size() > 6) {
                throw invalidParameter();
            }
            List<String> values = new java.util.ArrayList<>();
            for (JsonNode item : array) {
                if (!item.isTextual() || !item.asText().matches("^[1-9][0-9]*$")) {
                    throw invalidParameter();
                }
                values.add(item.asText());
            }
            if (values.stream().distinct().count() != values.size()) {
                throw invalidParameter();
            }
            return values.stream().sorted(Comparator.comparingLong(Long::parseLong)).toList();
        } catch (JsonProcessingException exception) {
            throw invalidParameter();
        }
    }

    private static BusinessException invalidParameter() {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }
}
