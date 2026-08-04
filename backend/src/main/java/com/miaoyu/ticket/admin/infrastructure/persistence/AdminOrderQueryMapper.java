package com.miaoyu.ticket.admin.infrastructure.persistence;

import com.miaoyu.ticket.admin.application.AdminOrderRepository;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理订单只读SQL入口。
 *
 * <p>复杂动态筛选保存在同名XML中，便于独立审查选择字段、JOIN边界和批量条件。
 * 所有用户输入均通过MyBatis参数绑定，XML不得使用字符串替换语法。</p>
 */
@Mapper
public interface AdminOrderQueryMapper {

    /** 与分页SQL复用同一筛选片段，防止total和records条件漂移。 */
    long countOrders(@Param("criteria") AdminOrderRepository.Criteria criteria);

    /** SQL固定按创建时间和订单ID倒序，不接受客户端排序表达式。 */
    List<AdminOrderRepository.OrderSnapshot> findOrderPage(
            @Param("criteria") AdminOrderRepository.Criteria criteria);

    /** 订单号全局唯一；未命中时MyBatis返回null。 */
    AdminOrderRepository.OrderSnapshot findByOrderNo(@Param("orderNo") String orderNo);

    /** 批量条件最多来自一页100个订单ID。 */
    List<AdminOrderRepository.SeatSnapshot> findSeatsByOrderIds(
            @Param("orderIds") List<Long> orderIds);

    /** 支付查询主动排除idempotency_key。 */
    List<AdminOrderRepository.PaymentSnapshot> findPaymentsByOrderIds(
            @Param("orderIds") List<Long> orderIds);

    /** 电子票查询主动排除qr_payload。 */
    List<AdminOrderRepository.TicketSnapshot> findTicketsByOrderIds(
            @Param("orderIds") List<Long> orderIds);

    /** 退款查询主动排除idempotency_key、action_id和impact_snapshot。 */
    List<AdminOrderRepository.RefundSnapshot> findRefundsByOrderIds(
            @Param("orderIds") List<Long> orderIds);
}
