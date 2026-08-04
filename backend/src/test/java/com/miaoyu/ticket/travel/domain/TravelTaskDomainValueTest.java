package com.miaoyu.ticket.travel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TravelTaskDomainValueTest {

    @Test
    void shouldNormalizeExternalTaskIdWithoutChangingItsStringNature() {
        TravelTaskId taskId = new TravelTaskId("  9007199254740993001  ");

        // 对外任务号必须保留字符串，防止前端把大整数转换后丢失精度。
        assertThat(taskId.value()).isEqualTo("9007199254740993001");
    }

    @Test
    void shouldRejectBlankOrTooLongTaskId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TravelTaskId("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new TravelTaskId("a".repeat(65)));
    }

    @Test
    void shouldKeepVersionsNonNegativeAndTaskVersionMonotonic() {
        assertThat(new TravelTaskVersion(3).next().value()).isEqualTo(4);
        assertThat(new OrderVersion(4).isAtLeast(new OrderVersion(3))).isTrue();
        assertThat(new OrderVersion(2).isAtLeast(new OrderVersion(3))).isFalse();

        assertThatIllegalArgumentException().isThrownBy(() -> new TravelTaskVersion(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderVersion(-1));
    }

    @Test
    void shouldIdentifyOnlyClosedTaskStatesAsTerminal() {
        assertThat(TravelTaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TravelTaskStatus.CANCELLED.requiresClosedAt()).isTrue();
        assertThat(TravelTaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TravelTaskStatus.NOTIFIED.isTerminal()).isFalse();
        assertThat(TravelTaskStatus.READY.requiresClosedAt()).isFalse();
    }
}
