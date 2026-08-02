package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 04 排期座位模块错误码。 */
public enum TicketingErrorCode implements ErrorCode {
    SEAT_NOT_LOCKABLE(204001, "座位不可锁定", HttpStatus.CONFLICT),
    SHOW_NOT_SALEABLE(204002, "场次不可售或已开场", HttpStatus.CONFLICT),
    SEAT_SNAPSHOT_STALE(204003, "座位快照版本已过期", HttpStatus.CONFLICT),
    QUERY_UNAVAILABLE(306003, "票务查询暂不可用", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    TicketingErrorCode(int code, String message, HttpStatus httpStatus) {
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
