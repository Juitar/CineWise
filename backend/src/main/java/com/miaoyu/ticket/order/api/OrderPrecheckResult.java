package com.miaoyu.ticket.order.api;

/** 预检只暴露是否可执行和稳定错误码，不暴露价格、库存细节或订单数据。 */
public record OrderPrecheckResult(boolean executable, Integer errorCode) {

    public OrderPrecheckResult {
        if (executable == (errorCode != null)) {
            throw new IllegalArgumentException("预检成功只能没有错误码，失败必须有错误码");
        }
    }

    public static OrderPrecheckResult allowed() {
        return new OrderPrecheckResult(true, null);
    }

    public static OrderPrecheckResult rejected(int errorCode) {
        return new OrderPrecheckResult(false, errorCode);
    }
}
