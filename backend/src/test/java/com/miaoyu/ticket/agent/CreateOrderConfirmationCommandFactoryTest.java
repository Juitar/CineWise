package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationCommandFactory;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreateOrderConfirmationCommandFactoryTest {
    @Test
    void shouldParseAndSortAConfirmedSeatJsonArray() {
        var command = new CreateOrderConfirmationCommandFactory(new ObjectMapper()).create(node("[\"9\",\"2\"]"));

        assertThat(command.showId()).isEqualTo("70001");
        assertThat(command.sortedSeatIds()).containsExactly("2", "9");
    }

    @Test
    void shouldRejectInvalidOrDuplicatedSeatIdsWith100001() {
        var factory = new CreateOrderConfirmationCommandFactory(new ObjectMapper());

        assertThatThrownBy(() -> factory.create(node("[\"2\",\"2\"]")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode().code()).isEqualTo(100001));
        assertThatThrownBy(() -> factory.create(node("4,5")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode().code()).isEqualTo(100001));
    }

    private static ExecutionPlanNode node(String seatIds) {
        return new ExecutionPlanNode("write", PlanNodeType.CALL_TOOL, "createOrder",
                List.of(new InputReference("showId", InputReferenceSource.SLOT, "showId"),
                        new InputReference("seatIds", InputReferenceSource.SLOT, "seatIds")),
                List.of("confirm"), FailurePolicy.FAIL, PlanNodeStatus.PENDING, true, false, null, null,
                new SlotSnapshot(1L, Map.of("showId", "70001", "seatIds", seatIds)));
    }
}
