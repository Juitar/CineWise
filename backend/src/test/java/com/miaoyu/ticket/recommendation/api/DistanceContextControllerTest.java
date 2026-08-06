package com.miaoyu.ticket.recommendation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.recommendation.application.DistanceContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DistanceContextControllerTest {

    /*
     * Controller 只映射 D 应用服务已分类的结果，不读取认证或位置持久化数据。
     * 400、404、409 让 C 能分别提示输入错误、重新申请定位和避免重复提交。
     */
    private DistanceContextService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(DistanceContextService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DistanceContextController(service)).build();
    }

    @Test
    void returnsConfiguredStatusForNotFoundAndRepeatedUpload() throws Exception {
        when(service.upload(any(), any(), any())).thenReturn(DistanceContextService.UploadResult.NOT_FOUND);
        mockMvc.perform(upload("missing")).andExpect(status().isNotFound());

        when(service.upload(any(), any(), any())).thenReturn(DistanceContextService.UploadResult.CONFLICT);
        mockMvc.perform(upload("used")).andExpect(status().isConflict());
    }

    @Test
    void returnsBadRequestForInvalidCoordinate() throws Exception {
        when(service.upload(any(), any(), any())).thenThrow(new IllegalArgumentException("越界"));

        mockMvc.perform(upload("valid-id")).andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(String contextId) {
        return post("/api/v1/recommendation/distance-contexts/{distanceContextId}/location", contextId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longitude\":112.938814,\"latitude\":28.228209}");
    }
}
