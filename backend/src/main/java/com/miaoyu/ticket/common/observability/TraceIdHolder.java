package com.miaoyu.ticket.common.observability;

import org.slf4j.MDC;

/** traceId 的统一读取入口。 */
public final class TraceIdHolder {

    public static final String MDC_KEY = "traceId";

    private TraceIdHolder() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }
}
