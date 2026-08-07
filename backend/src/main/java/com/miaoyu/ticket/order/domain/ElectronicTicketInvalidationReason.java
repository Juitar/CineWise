package com.miaoyu.ticket.order.domain;

/**
 * 电子票进入 INVALIDATED 的可信业务原因。
 *
 * <p>状态本身不足以表达用户提示：场次结束与管理员发现异常都可导致失效，前端不得自行猜测。</p>
 */
public enum ElectronicTicketInvalidationReason {
    SHOW_ENDED,
    ADMIN_INVALIDATED
}
