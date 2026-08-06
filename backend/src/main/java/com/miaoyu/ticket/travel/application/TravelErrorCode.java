package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** D 负责的出行模块错误码；页面按数值 code 判断，不依赖异常文本。 */
public enum TravelErrorCode implements ErrorCode {
    TASK_NOT_FOUND(207001, "出行任务不存在或无权访问", HttpStatus.NOT_FOUND),
    TASK_CANCELLED(207002, "出行任务已取消", HttpStatus.CONFLICT),
    TASK_VERSION_CONFLICT(207003, "出行任务已更新，请刷新后重试", HttpStatus.CONFLICT),
    /** A 的订单摘要或 D 的内容摘要不可用时，统一隐藏内部错误。 */
    DEPENDENCY_UNAVAILABLE(207004, "订单或内容摘要暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    REFRESH_TOO_FREQUENT(107001, "建议刷新过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    ROUTE_SHARING_NOT_CONFIRMED(107002, "请先确认位置共享说明", HttpStatus.UNPROCESSABLE_ENTITY),
    FOOD_RADIUS_OUT_OF_RANGE(107003, "餐饮查询半径不在允许范围内", HttpStatus.BAD_REQUEST),
    ROUTE_SERVICE_UNAVAILABLE(307001, "路线服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    TravelErrorCode(int code, String message, HttpStatus httpStatus) {
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
