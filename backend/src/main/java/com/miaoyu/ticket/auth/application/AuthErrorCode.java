package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 认证模块公开错误码，避免修改其他模块已经使用的公共错误码。 */
public enum AuthErrorCode implements ErrorCode {
    INVALID_PARAMETER(101001, "请求参数不合法", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(201001, "邮箱或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_UNAVAILABLE(201005, "账号已禁用或锁定", HttpStatus.FORBIDDEN),
    SESSION_INVALID(201006, "登录状态已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(201007, "当前账号无权执行该操作", HttpStatus.FORBIDDEN),
    CSRF_INVALID(201009, "安全校验已失效，请重新操作", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
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
