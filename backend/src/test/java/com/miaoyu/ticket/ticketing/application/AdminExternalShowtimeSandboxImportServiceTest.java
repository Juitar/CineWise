package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminExternalShowtimeSandboxImportServiceTest {

    private static final LocalDate SHOW_DATE = LocalDate.of(2026, 8, 10);

    @Test
    void givenAdministratorAndRepeatedCinemaIds_whenImportReferences_thenDeduplicateBeforeCallingImportPlan() {
        ExternalShowtimeSandboxImportApplicationService importApplicationService = mock(
                ExternalShowtimeSandboxImportApplicationService.class);
        ExternalShowtimeSandboxImportApplicationService.ImportResult expected = new
                ExternalShowtimeSandboxImportApplicationService.ImportResult(List.of(10001L, 10002L), false);
        when(importApplicationService.importReferences(SHOW_DATE, List.of(2001L, 2002L))).thenReturn(expected);
        AdminExternalShowtimeSandboxImportService service = serviceFor(RoleCode.ADMIN, importApplicationService);

        ExternalShowtimeSandboxImportApplicationService.ImportResult actual = service.importReferences(
                SHOW_DATE,
                List.of("2001", "2001", "2002"));

        assertThat(actual).isEqualTo(expected);
        verify(importApplicationService).importReferences(eq(SHOW_DATE), eq(List.of(2001L, 2002L)));
    }

    @Test
    void givenOrdinaryUser_whenImportReferences_thenRejectBeforeCallingExternalImport() {
        ExternalShowtimeSandboxImportApplicationService importApplicationService = mock(
                ExternalShowtimeSandboxImportApplicationService.class);
        AdminExternalShowtimeSandboxImportService service = serviceFor(RoleCode.USER, importApplicationService);

        assertThatThrownBy(() -> service.importReferences(SHOW_DATE, List.of("2001")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(importApplicationService);
    }

    @Test
    void givenNonCanonicalOrOverflowCinemaId_whenImportReferences_thenRejectBeforeCallingExternalImport() {
        ExternalShowtimeSandboxImportApplicationService importApplicationService = mock(
                ExternalShowtimeSandboxImportApplicationService.class);
        AdminExternalShowtimeSandboxImportService service = serviceFor(RoleCode.ADMIN, importApplicationService);

        assertInvalidCinemaId(service, "0");
        assertInvalidCinemaId(service, "02001");
        assertInvalidCinemaId(service, "9223372036854775808");
        verifyNoInteractions(importApplicationService);
    }

    private void assertInvalidCinemaId(AdminExternalShowtimeSandboxImportService service, String cinemaId) {
        assertThatThrownBy(() -> service.importReferences(SHOW_DATE, List.of(cinemaId)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    private AdminExternalShowtimeSandboxImportService serviceFor(
            RoleCode role,
            ExternalShowtimeSandboxImportApplicationService importApplicationService) {
        CurrentUserAccessor currentUserAccessor = () -> new CurrentUser(9000001L, role, 0L);
        return new AdminExternalShowtimeSandboxImportService(currentUserAccessor, importApplicationService);
    }
}
