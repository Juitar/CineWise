package com.miaoyu.ticket.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void shouldNormalizeAndMaskEmail() {
        String normalized = EmailAddress.normalize("  Alice.Example@CineWise.Test  ");

        assertThat(normalized).isEqualTo("alice.example@cinewise.test");
        assertThat(EmailAddress.mask(normalized)).isEqualTo("a***@cinewise.test");
    }

    @Test
    void shouldRejectInvalidEmail() {
        assertThatIllegalArgumentException().isThrownBy(() -> EmailAddress.normalize("not-an-email"));
        assertThatIllegalArgumentException().isThrownBy(() -> EmailAddress.normalize("a@localhost"));
    }
}
