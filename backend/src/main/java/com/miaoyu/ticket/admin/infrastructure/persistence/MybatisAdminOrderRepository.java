package com.miaoyu.ticket.admin.infrastructure.persistence;

import com.miaoyu.ticket.admin.application.AdminOrderRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 管理订单Repository的MyBatis只读适配器。
 *
 * <p>适配器不捕获数据库异常并返回空结果；基础设施故障必须继续交给统一异常层，
 * 避免管理员把查询故障误判为没有交易记录。</p>
 *
 * <p>返回集合统一复制为不可变列表，防止Mapper可变结果在事务结束后被修改。
 * Optional只表达单笔订单是否存在，不捕获SQL执行失败。</p>
 */
@Repository
public class MybatisAdminOrderRepository implements AdminOrderRepository {

    private final AdminOrderQueryMapper mapper;

    public MybatisAdminOrderRepository(AdminOrderQueryMapper mapper) {
        this.mapper = mapper;
    }

    /** COUNT与分页必须使用XML中的同一筛选片段。 */
    @Override
    public long countOrders(Criteria criteria) {
        return mapper.countOrders(criteria);
    }

    /** 主分页已经固定排序，适配器不进行二次内存排序。 */
    @Override
    public List<OrderSnapshot> findOrderPage(Criteria criteria) {
        return List.copyOf(mapper.findOrderPage(criteria));
    }

    /** null只代表订单号未命中，数据库异常会直接向上抛出。 */
    @Override
    public Optional<OrderSnapshot> findByOrderNo(String orderNo) {
        return Optional.ofNullable(mapper.findByOrderNo(orderNo));
    }

    /** 座位批次仅由详情调用，并保持SQL中的稳定顺序。 */
    @Override
    public List<SeatSnapshot> findSeatsByOrderIds(List<Long> orderIds) {
        return List.copyOf(mapper.findSeatsByOrderIds(orderIds));
    }

    /** 支付批次不会选择或传递幂等键。 */
    @Override
    public List<PaymentSnapshot> findPaymentsByOrderIds(List<Long> orderIds) {
        return List.copyOf(mapper.findPaymentsByOrderIds(orderIds));
    }

    /** 电子票批次不会选择或传递二维码载荷。 */
    @Override
    public List<TicketSnapshot> findTicketsByOrderIds(List<Long> orderIds) {
        return List.copyOf(mapper.findTicketsByOrderIds(orderIds));
    }

    /** 退款批次不会选择或传递内部影响快照和确认标识。 */
    @Override
    public List<RefundSnapshot> findRefundsByOrderIds(List<Long> orderIds) {
        return List.copyOf(mapper.findRefundsByOrderIds(orderIds));
    }
}
