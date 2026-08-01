package com.miaoyu.ticket.common.error;

import org.springframework.http.HttpStatus;

/** 模块编码 00 的公共错误码。 */
public enum CommonErrorCode implements ErrorCode {
    INVALID_PARAMETER(100001, "请求参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(100401, "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(100403, "无权执行该操作", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(100404, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(100405, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(100409, "资源状态冲突", HttpStatus.CONFLICT),
    RATE_LIMITED(100429, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(300001, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
