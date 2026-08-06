package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SyncTaskStateValidatorTest {
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Test
    void givenLegalTerminalCombinations_whenValidated_thenTheyAreAccepted() {
        assertThatCode(() -> SyncTaskStateValidator.validateTerminal(ContentSyncTaskPort.SyncTaskStatus.SUCCESS,
                2, 2, 0, null, null, FINISHED_AT)).doesNotThrowAnyException();
        assertThatCode(() -> SyncTaskStateValidator.validateTerminal(ContentSyncTaskPort.SyncTaskStatus.FAILED,
                0, 0, 0, 303004, ContentSyncTaskPort.FailureCategory.NETWORK, FINISHED_AT))
                .doesNotThrowAnyException();
        assertThatCode(() -> SyncTaskStateValidator.validateTerminal(ContentSyncTaskPort.SyncTaskStatus.PARTIAL,
                2, 1, 1, 303004, ContentSyncTaskPort.FailureCategory.DATA_VALIDATION, FINISHED_AT))
                .doesNotThrowAnyException();
    }

    @Test
    void givenIllegalTerminalCombinations_whenValidated_thenTheyAreRejectedBeforeJdbcWrite() {
        assertThatIllegalArgumentException().isThrownBy(() -> SyncTaskStateValidator.validateTerminal(
                ContentSyncTaskPort.SyncTaskStatus.SUCCESS, 1, 1, 0, 303004,
                ContentSyncTaskPort.FailureCategory.INTERNAL, FINISHED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> SyncTaskStateValidator.validateTerminal(
                ContentSyncTaskPort.SyncTaskStatus.PARTIAL, 2, 1, 1, 303004, null, FINISHED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> SyncTaskStateValidator.validateTerminal(
                ContentSyncTaskPort.SyncTaskStatus.FAILED, 1, 1, 0, 303004,
                ContentSyncTaskPort.FailureCategory.INTERNAL, FINISHED_AT));
    }

    @Test
    void givenRunningWithoutLease_whenValidated_thenItIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> SyncTaskStateValidator.validateRunning("",
                FINISHED_AT.plusSeconds(90), FINISHED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> SyncTaskStateValidator.validateRunning("owner",
                FINISHED_AT, FINISHED_AT));
    }
}
