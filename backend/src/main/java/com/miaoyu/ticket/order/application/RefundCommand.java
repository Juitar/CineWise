package com.miaoyu.ticket.order.application;

/**
 * 传统页面提交的退票命令。
 *
 * <ul>
 *   <li>orderNo只用于定位当前用户资源，不能替代服务端归属校验；</li>
 *   <li>refundReason仅作演示记录，不参与退款金额计算；</li>
 *   <li>clientRequestId随影响确认生成，用于识别原页面动作；</li>
 *   <li>idempotencyKey控制网络重放，参数不同时必须拒绝复用。</li>
 * </ul>
 * <p>{@code actionId}只为未来Agent适配层保留；REST入口不把它当成已验证的确认凭证。</p>
 */
public record RefundCommand(
        String orderNo,
        String refundReason,
        String clientRequestId,
        String actionId,
        String idempotencyKey) {
}
