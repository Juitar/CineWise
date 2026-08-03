package com.miaoyu.ticket.order.application;

import java.util.List;

/**
 * 替代场次查询结果。
 *
 * <ul>
 *   <li>orderNo保留查询来源，便于页面拒绝旧订单响应；</li>
 *   <li>列表已由数据库按开场时间和场次ID稳定排序；</li>
 *   <li>构造时防御性复制，避免Controller或测试修改已返回快照。</li>
 * </ul>
 * <p>空列表是合法结果，不能为维持页面展示而伪造可售场次。</p>
 */
public record AlternativeShowsView(String orderNo, List<AlternativeShowView> shows) {

    public AlternativeShowsView {
        shows = List.copyOf(shows);
    }
}
