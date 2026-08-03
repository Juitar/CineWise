package com.miaoyu.ticket.order.infrastructure.persistence;

import com.miaoyu.ticket.order.application.PaymentRepository;
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
        return Optional.ofNullable(mapper.findPaymentByOrderId(orderId)).map(this::toPaymentSnapshot);
    }

    @Override
    public Optional<PaymentSnapshot> findPaymentByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mapper.findPaymentByIdempotencyKey(idempotencyKey))
                .map(this::toPaymentSnapshot);
    }

    @Override
    public Optional<TicketSnapshot> findTicketByOrderId(long orderId) {
        return Optional.ofNullable(mapper.findTicketByOrderId(orderId)).map(this::toTicketSnapshot);
    }

    @Override
    public Optional<TicketSnapshot> findTicketByIdAndUser(long ticketId, long userId) {
        return Optional.ofNullable(mapper.findTicketByIdAndUser(ticketId, userId))
                .map(this::toTicketSnapshot);
    }

    @Override
    public void insertProcessingPayment(NewPayment payment) {
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
        return mapper.completePayment(paymentId, expectedVersion, paidAt) == 1;
    }

    @Override
    public void insertTicket(NewTicket ticket) {
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

    private PaymentSnapshot toPaymentSnapshot(PaymentSnapshotRow row) {
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
        return new TicketSnapshot(
                row.ticketId(),
                row.ticketCode(),
                row.orderId(),
                row.userId(),
                ElectronicTicketStatus.valueOf(row.status()),
                row.qrPayload(),
                row.issuedAt(),
                row.invalidatedAt(),
                row.version(),
                row.updatedAt());
    }
}
