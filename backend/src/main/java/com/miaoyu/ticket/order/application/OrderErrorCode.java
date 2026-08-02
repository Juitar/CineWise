package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 05 订单支付模块错误码，编号与A详细设计一致。 */
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(205001, "订单不存在或无权访问", HttpStatus.NOT_FOUND),
    ORDER_STATE_CONFLICT(205002, "订单状态不允许当前操作", HttpStatus.CONFLICT),
    ORDER_EXPIRED(205003, "订单已过期", HttpStatus.CONFLICT),
    CONFIRMATION_INVALID(205004, "确认凭证无效", HttpStatus.UNPROCESSABLE_ENTITY),
    IDEMPOTENCY_PARAMETER_MISMATCH(205005, "幂等请求参数不一致", HttpStatus.CONFLICT),
    ORDER_NOT_REFUNDABLE(205006, "订单不可退", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    OrderErrorCode(int code, String message, HttpStatus httpStatus) {
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
