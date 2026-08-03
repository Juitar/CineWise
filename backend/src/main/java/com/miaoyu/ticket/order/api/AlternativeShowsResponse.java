package com.miaoyu.ticket.order.api;

import java.util.List;

/**
 * 替代场次集合。
 *
 * <ul>
 *   <li>orderNo帮助页面拒绝旧订单查询响应；</li>
 *   <li>候选保持应用服务的稳定排序；</li>
 *   <li>构造时复制集合，避免序列化期间被修改；</li>
 *   <li>无候选时返回空数组而不是业务错误。</li>
 * </ul>
 */
public record AlternativeShowsResponse(String orderNo, List<AlternativeShowResponse> shows) {

    public AlternativeShowsResponse {
        shows = List.copyOf(shows);
    }
}
