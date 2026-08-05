package com.miaoyu.ticket.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class VerificationCodeSecurityTest {

    @Test
    void shouldGenerateSixDigitCodeIncludingLeadingZero() {
        SecureRandom random = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };

        assertThat(new SecureNumericVerificationCodeGenerator(random, 6).generate()).isEqualTo("000042");
    }

    @Test
    void shouldIsolateHashByEmailPurposeAndSecret() {
        HmacVerificationCodeHasher hasher =
                new HmacVerificationCodeHasher("test-verification-secret-at-least-32-bytes-long");
        String loginHash = hasher.hash("user@cinewise.test", VerificationPurpose.LOGIN, "123456");

        assertThat(loginHash).hasSize(64);
        assertThat(hasher.matches("user@cinewise.test", VerificationPurpose.LOGIN, "123456", loginHash))
                .isTrue();
        assertThat(hasher.matches("other@cinewise.test", VerificationPurpose.LOGIN, "123456", loginHash))
                .isFalse();
        assertThat(hasher.matches("user@cinewise.test", VerificationPurpose.REGISTER, "123456", loginHash))
                .isFalse();
        assertThat(hasher.matches("user@cinewise.test", VerificationPurpose.LOGIN, "654321", loginHash))
                .isFalse();
    }
}
