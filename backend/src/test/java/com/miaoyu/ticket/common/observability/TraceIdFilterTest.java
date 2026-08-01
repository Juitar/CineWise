package com.miaoyu.ticket.common.observability;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class TraceIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReuseSafeCallerTraceId() throws Exception {
        mockMvc.perform(get("/probe").header(TraceIdFilter.HEADER_NAME, "trace-12345678"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.HEADER_NAME, "trace-12345678"))
                .andExpect(jsonPath("$.traceId").value("trace-12345678"));
    }

    @Test
    void shouldReplaceUnsafeTraceId() throws Exception {
        mockMvc.perform(get("/probe").header(TraceIdFilter.HEADER_NAME, "bad trace\nvalue"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.HEADER_NAME, matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.traceId", matchesPattern("[a-f0-9]{32}")));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        Result<String> probe() {
            return Result.success("ok");
        }
    }
}
