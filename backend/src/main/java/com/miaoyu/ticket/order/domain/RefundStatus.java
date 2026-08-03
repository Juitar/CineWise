package com.miaoyu.ticket.order.domain;

/**
 * 模拟退款的权威状态。
 *
 * <p>状态只允许按以下顺序迁移：</p>
 * <ul>
 *   <li>REQUESTED：唯一退款记录已经创建，其他权威资源尚未形成退款终态；</li>
 *   <li>PROCESSING：订单已进入REFUNDING，事务正在同步更新票和座位；</li>
 *   <li>SUCCESS：订单、电子票、座位和退款记录已经在同一事务形成一致终态。</li>
 * </ul>
 * <p>固定成功Mock退款没有业务失败终态。异常会回滚整笔事务，因此不能把HTTP失败或超时写成退款失败。</p>
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    SUCCESS
}
