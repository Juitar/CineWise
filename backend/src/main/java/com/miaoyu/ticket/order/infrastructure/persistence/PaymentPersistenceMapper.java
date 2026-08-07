package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Mock支付和电子票的显式SQL。 */
@Mapper
public interface PaymentPersistenceMapper {

    @Select("""
            SELECT id AS payment_id,
                   payment_no,
                   order_id,
                   idempotency_key,
                   amount,
                   status,
                   request_time,
                   paid_time,
                   version,
                   update_time AS updated_at
              FROM mock_payment
             WHERE order_id = #{orderId}
            """)
    PaymentSnapshotRow findPaymentByOrderId(@Param("orderId") long orderId);

    @Select("""
            SELECT id AS payment_id,
                   payment_no,
                   order_id,
                   idempotency_key,
                   amount,
                   status,
                   request_time,
                   paid_time,
                   version,
                   update_time AS updated_at
              FROM mock_payment
             WHERE idempotency_key = #{idempotencyKey}
            """)
    PaymentSnapshotRow findPaymentByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id AS ticket_id,
                   ticket_code,
                   order_id,
                   user_id,
                   status,
                   qr_payload,
                   issued_time AS issued_at,
                   invalidated_time AS invalidated_at,
                   invalidation_reason,
                   version,
                   update_time AS updated_at
              FROM electronic_ticket
             WHERE order_id = #{orderId}
            """)
    TicketSnapshotRow findTicketByOrderId(@Param("orderId") long orderId);

    @Select("""
            SELECT id AS ticket_id,
                   ticket_code,
                   order_id,
                   user_id,
                   status,
                   qr_payload,
                   issued_time AS issued_at,
                   invalidated_time AS invalidated_at,
                   invalidation_reason,
                   version,
                   update_time AS updated_at
              FROM electronic_ticket
             WHERE id = #{ticketId}
               AND user_id = #{userId}
            """)
    TicketSnapshotRow findTicketByIdAndUser(
            @Param("ticketId") long ticketId,
            @Param("userId") long userId);

    @Insert("""
            INSERT INTO mock_payment (
                id, payment_no, order_id, idempotency_key,
                amount, status, request_time, paid_time,
                version, create_time, update_time
            ) VALUES (
                #{row.paymentId}, #{row.paymentNo}, #{row.orderId}, #{row.idempotencyKey},
                #{row.amount}, 'PROCESSING', #{row.requestedAt}, NULL,
                0, #{row.requestedAt}, #{row.requestedAt}
            )
            """)
    int insertProcessingPayment(@Param("row") PaymentInsertRow row);

    @Update("""
            UPDATE mock_payment
               SET status = 'SUCCESS',
                   paid_time = #{paidAt},
                   version = version + 1,
                   update_time = #{paidAt}
             WHERE id = #{paymentId}
               AND status = 'PROCESSING'
               AND version = #{expectedVersion}
            """)
    int completePayment(
            @Param("paymentId") long paymentId,
            @Param("expectedVersion") int expectedVersion,
            @Param("paidAt") LocalDateTime paidAt);

    @Insert("""
            INSERT INTO electronic_ticket (
                id, ticket_code, order_id, user_id,
                status, qr_payload, issued_time, invalidated_time,
                version, create_time, update_time
            ) VALUES (
                #{row.ticketId}, #{row.ticketCode}, #{row.orderId}, #{row.userId},
                'VALID', #{row.qrPayload}, #{row.issuedAt}, NULL,
                0, #{row.issuedAt}, #{row.issuedAt}
            )
            """)
    int insertTicket(@Param("row") TicketInsertRow row);

    /** 票状态和版本共同保证重复退款不会再次修改电子票。 */
    @Update("""
            UPDATE electronic_ticket
               SET status = 'REFUNDED',
                   invalidated_time = #{invalidatedAt},
                   version = version + 1,
                   update_time = #{invalidatedAt}
             WHERE id = #{ticketId}
               AND status = 'VALID'
               AND version = #{expectedVersion}
            """)
    int refundTicket(
            @Param("ticketId") long ticketId,
            @Param("expectedVersion") int expectedVersion,
            @Param("invalidatedAt") LocalDateTime invalidatedAt);
}
