package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.miaoyu.ticket.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class PasswordResetApplicationServiceTest {

    private final PasswordResetTransaction transaction = mock(PasswordResetTransaction.class);
    private final PasswordResetApplicationService service = new PasswordResetApplicationService(transaction);

    @Test
    void shouldNormalizeEmailAndDelegateValidReset() {
        service.reset(new PasswordResetCommand(
                "reset-1", " User@CineWise.Test ", "123456", "NewPassword1"));

        verify(transaction).execute("user@cinewise.test", "123456", "NewPassword1");
    }

    @Test
    void shouldRejectWeakPasswordBeforeConsumingCode() {
        assertThatThrownBy(() -> service.reset(new PasswordResetCommand(
                        "reset-1", "user@cinewise.test", "123456", "password")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_PARAMETER));

        verify(transaction, never()).execute("user@cinewise.test", "123456", "password");
    }

    @Test
    void shouldRejectMalformedEmailCodeAndRequestId() {
        assertInvalid(new PasswordResetCommand("", "user@cinewise.test", "123456", "NewPassword1"));
        assertInvalid(new PasswordResetCommand("reset-1", "invalid", "123456", "NewPassword1"));
        assertInvalid(new PasswordResetCommand("reset-1", "user@cinewise.test", "12345", "NewPassword1"));
    }

    private void assertInvalid(PasswordResetCommand command) {
        assertThatThrownBy(() -> service.reset(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_PARAMETER));
    }
}
