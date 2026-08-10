package com.miaoyu.ticket.order.infrastructure.persistence;

import com.miaoyu.ticket.order.application.PaymentRepository;
import com.miaoyu.ticket.order.domain.ElectronicTicketInvalidationReason;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将支付和电子票持久化投影映射为应用层快照。 */
@Repository
public class MybatisPaymentRepository implements PaymentRepository {

    private final PaymentPersistenceMapper mapper;

    public MybatisPaymentRepository(PaymentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PaymentSnapshot> findPaymentByOrderId(long orderId) {
        // 支付记录按订单读取只供订单交易流程使用，用户侧访问还要经过订单归属校验。
        return Optional.ofNullable(mapper.findPaymentByOrderId(orderId)).map(this::toPaymentSnapshot);
    }

    @Override
    public Optional<PaymentSnapshot> findPaymentByIdempotencyKey(String idempotencyKey) {
        // 支付请求重试用幂等键恢复同一支付记录，不能创建第二笔 Mock 支付。
        return Optional.ofNullable(mapper.findPaymentByIdempotencyKey(idempotencyKey))
                .map(this::toPaymentSnapshot);
    }

    @Override
    public Optional<TicketSnapshot> findTicketByOrderId(long orderId) {
        return Optional.ofNullable(mapper.findTicketByOrderId(orderId)).map(this::toTicketSnapshot);
    }

    @Override
    public Optional<TicketSnapshot> findTicketByIdAndUser(long ticketId, long userId) {
        // 电子票详情在 SQL 层携带 userId，避免仅凭票主键越权读取二维码内容。
        return Optional.ofNullable(mapper.findTicketByIdAndUser(ticketId, userId))
                .map(this::toTicketSnapshot);
    }

    @Override
    public void insertProcessingPayment(NewPayment payment) {
        // 支付先落 PROCESSING 记录，支付完成再依靠版本条件推进，避免回调重放重复完成。
        int inserted = mapper.insertProcessingPayment(new PaymentInsertRow(
                payment.paymentId(),
                payment.paymentNo(),
                payment.orderId(),
                payment.idempotencyKey(),
                payment.amount(),
                payment.requestedAt()));
        if (inserted != 1) {
            throw new IllegalStateException("Mock支付记录写入行数异常");
        }
    }

    @Override
    public boolean completePayment(long paymentId, int expectedVersion, LocalDateTime paidAt) {
        // 返回 false 代表状态或版本已变化，由应用层重新读取并决定是否幂等返回。
        return mapper.completePayment(paymentId, expectedVersion, paidAt) == 1;
    }

    @Override
    public void insertTicket(NewTicket ticket) {
        // 电子票与支付成功在同一事务内生成，写入失败必须整体回滚支付完成状态。
        int inserted = mapper.insertTicket(new TicketInsertRow(
                ticket.ticketId(),
                ticket.ticketCode(),
                ticket.orderId(),
                ticket.userId(),
                ticket.qrPayload(),
                ticket.issuedAt()));
        if (inserted != 1) {
            throw new IllegalStateException("电子票写入行数异常");
        }
    }

    @Override
    public boolean refundTicket(long ticketId, int expectedVersion, LocalDateTime invalidatedAt) {
        // 退款电子票只允许按期望版本作废，重复退款不会覆盖首次作废时间。
        return mapper.refundTicket(ticketId, expectedVersion, invalidatedAt) == 1;
    }

    private PaymentSnapshot toPaymentSnapshot(PaymentSnapshotRow row) {
        // 字符串支付状态在持久化边界转为枚举，数据异常不能静默展示为未知状态。
        return new PaymentSnapshot(
                row.paymentId(),
                row.paymentNo(),
                row.orderId(),
                row.idempotencyKey(),
                row.amount(),
                PaymentStatus.valueOf(row.status()),
                row.requestTime(),
                row.paidTime(),
                row.version(),
                row.updatedAt());
    }

    private TicketSnapshot toTicketSnapshot(TicketSnapshotRow row) {
        // 作废原因允许为空，只在确有退款或其他失效动作时转换为枚举。
        return new TicketSnapshot(
                row.ticketId(),
                row.ticketCode(),
                row.orderId(),
                row.userId(),
                ElectronicTicketStatus.valueOf(row.status()),
                row.qrPayload(),
                row.issuedAt(),
                row.invalidatedAt(),
                row.invalidationReason() == null
                        ? null
                        : ElectronicTicketInvalidationReason.valueOf(row.invalidationReason()),
                row.version(),
                row.updatedAt());
    }

}
