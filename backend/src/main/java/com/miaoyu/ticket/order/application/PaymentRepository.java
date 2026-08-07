package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.ElectronicTicketInvalidationReason;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/** Mock支付与电子票权威持久化端口。 */
public interface PaymentRepository {

    Optional<PaymentSnapshot> findPaymentByOrderId(long orderId);

    Optional<PaymentSnapshot> findPaymentByIdempotencyKey(String idempotencyKey);

    Optional<TicketSnapshot> findTicketByOrderId(long orderId);

    /** 电子票查询在SQL层同时校验当前用户归属，避免暴露其他用户票号。 */
    Optional<TicketSnapshot> findTicketByIdAndUser(long ticketId, long userId);

    void insertProcessingPayment(NewPayment payment);

    boolean completePayment(long paymentId, int expectedVersion, LocalDateTime paidAt);

    void insertTicket(NewTicket ticket);

    /** 退款事务只能将当前仍有效的电子票失效为REFUNDED。 */
    boolean refundTicket(long ticketId, int expectedVersion, LocalDateTime invalidatedAt);

    record PaymentSnapshot(
            long paymentId,
            String paymentNo,
            long orderId,
            String idempotencyKey,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime requestTime,
            LocalDateTime paidTime,
            int version,
            LocalDateTime updatedAt) {
    }

    record TicketSnapshot(
            long ticketId,
            String ticketCode,
            long orderId,
            long userId,
            ElectronicTicketStatus status,
            String qrPayload,
            LocalDateTime issuedAt,
            LocalDateTime invalidatedAt,
            ElectronicTicketInvalidationReason invalidationReason,
            int version,
            LocalDateTime updatedAt) {
    }

    record NewPayment(
            long paymentId,
            String paymentNo,
            long orderId,
            String idempotencyKey,
            BigDecimal amount,
            LocalDateTime requestedAt) {
    }

    record NewTicket(
            long ticketId,
            String ticketCode,
            long orderId,
            long userId,
            String qrPayload,
            LocalDateTime issuedAt) {
    }
}
