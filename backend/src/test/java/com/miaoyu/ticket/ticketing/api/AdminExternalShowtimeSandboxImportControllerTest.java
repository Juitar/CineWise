package com.miaoyu.ticket.ticketing.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.ticketing.application.AdminExternalShowtimeSandboxImportService;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeSandboxImportApplicationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Controller 只映射公开 JSON；ADMIN 角色复核属于应用服务的单元测试范围。 */
@WebMvcTest(AdminExternalShowtimeSandboxImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminExternalShowtimeSandboxImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminExternalShowtimeSandboxImportService importService;

    @Test
    void givenValidRequest_whenImportReferences_thenReturnOnlyLocalShowIdsAndTruncationState() throws Exception {
        LocalDate showDate = LocalDate.of(2026, 8, 10);
        when(importService.importReferences(showDate, List.of("2001", "2002"))).thenReturn(
                new ExternalShowtimeSandboxImportApplicationService.ImportResult(List.of(10001L, 10002L), false));

        mockMvc.perform(post("/api/v1/admin/ticketing/external-showtimes/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showDate":"2026-08-10","cinemaIds":["2001","2002"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.showIds[0]").value("10001"))
                .andExpect(jsonPath("$.data.showIds[1]").value("10002"))
                .andExpect(jsonPath("$.data.truncated").value(false));

        verify(importService).importReferences(eq(showDate), eq(List.of("2001", "2002")));
    }
}
