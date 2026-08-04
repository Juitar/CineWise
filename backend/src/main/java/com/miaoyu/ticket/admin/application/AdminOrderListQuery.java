package com.miaoyu.ticket.admin.application;

import java.time.LocalDate;

/**
 * 管理订单列表的白名单筛选输入。
 *
 * <p>这里保留REST原始字符串，让应用层统一完成trim、业务ID解析、
 * 状态枚举转换和分页上限校验，Repository永远只接收规范化条件。</p>
 *
 * <p>userKeyword为null表示请求没有携带筛选参数；空字符串表示调用方显式提交了
 * 非法条件。这个区别必须保留到应用层，不能由Web绑定提前合并。</p>
 *
 * <p>movieId和showId使用字符串承接REST业务ID，避免在进入Java前发生精度损失。</p>
 */
public record AdminOrderListQuery(
        String orderNo,
        String userKeyword,
        String status,
        String movieId,
        String showId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Integer page,
        Integer size) {
}
