package com.miaoyu.ticket.common.error;

import org.springframework.http.HttpStatus;

/** 公共错误码契约；模块错误码枚举必须实现本接口。 */
public interface ErrorCode {

    int code();

    String message();

    HttpStatus httpStatus();
}
