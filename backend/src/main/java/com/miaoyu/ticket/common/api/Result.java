package com.miaoyu.ticket.common.api;

import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.common.observability.TraceIdHolder;

/** REST 统一响应；业务失败的 {@code code} 使用六位数值领域错误码。 */
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data, TraceIdHolder.currentTraceId());
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static Result<Void> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, TraceIdHolder.currentTraceId());
    }

    public static Result<Void> failure(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, TraceIdHolder.currentTraceId());
    }
}
