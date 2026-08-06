package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectSeatsReplyFactsTest {
    @Test
    void shouldAcceptCompletePositiveLongDecimalBusinessIds() {
        new SelectSeatsReplyFacts("70001", "10001", "20001");
    }

    @Test
    void shouldRejectMissingOrInvalidBusinessIds() {
        for (String invalidValue : List.of("show-70001", "070001", "0", "-1", "", "not-a-number",
                "9223372036854775808")) {
            assertThatThrownBy(() -> new SelectSeatsReplyFacts(invalidValue, "10001", "20001"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new SelectSeatsReplyFacts("70001", null, "20001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectSeatsReplyFacts("70001", "10001", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectSeatsReplyFacts("70001", "bad", "20001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectSeatsReplyFacts("70001", "10001", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
