package com.miaoyu.ticket.ticketing.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.ticketing.application.AdminExternalShowtimeSandboxImportService;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeImportTaskRepository;
import com.miaoyu.ticket.ticketing.application.ExternalShowtimeImportTaskService;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
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
    void givenValidRequest_whenCreateImportTask_thenReturnTaskIdAndPendingState() throws Exception {
        LocalDate showDate = LocalDate.of(2026, 8, 10);
        when(importService.createTask(eq(showDate), eq(List.of("2001", "2002")), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new ExternalShowtimeImportTaskService.TaskView("task-1",
                        ExternalShowtimeImportTaskRepository.TaskStatus.PENDING, 0, 0, 0, false, null, null,
                        null, null));

        mockMvc.perform(post("/api/v1/admin/ticketing/external-showtimes/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showDate":"2026-08-10","cinemaIds":["2001","2002"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.truncated").value(false));

        verify(importService).createTask(
                eq(showDate), eq(List.of("2001", "2002")), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void givenMalformedTaskId_whenQuery_thenReturnBadRequest() throws Exception {
        when(importService.queryTask("bad-task-id"))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_PARAMETER));

        mockMvc.perform(get("/api/v1/admin/ticketing/external-showtimes/import/bad-task-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001));
    }

    @Test
    void givenMissingTask_whenQuery_thenReturnNotFound() throws Exception {
        when(importService.queryTask("550e8400-e29b-41d4-a716-446655440000"))
                .thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/ticketing/external-showtimes/import/"
                        + "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(100404));
    }
}
