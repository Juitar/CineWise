package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mock支付身份、输入校验、并发恢复与只读查询入口。 */
@Service
public class PaymentApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentApplicationService.class);
    private static final int MAXIMUM_KEY_LENGTH = 64;
    private static final int MAXIMUM_ORDER_NUMBER_LENGTH = 32;

    private final CurrentUserAccessor currentUserAccessor;
    private final PaymentTransaction paymentTransaction;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentViewFactory paymentViewFactory;

    public PaymentApplicationService(
            CurrentUserAccessor currentUserAccessor,
            PaymentTransaction paymentTransaction,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentViewFactory paymentViewFactory) {
        this.currentUserAccessor = currentUserAccessor;
        this.paymentTransaction = paymentTransaction;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentViewFactory = paymentViewFactory;
    }

    /** 唯一约束竞争失败后只查询已提交结果，不在异常事务中继续写入。 */
    public PaymentView pay(String orderNo, String idempotencyKey) {
        validatePaymentInput(orderNo, idempotencyKey);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        try {
            PaymentView result = paymentTransaction.pay(currentUserId, orderNo, idempotencyKey);
            LOGGER.info(
                    "Mock支付处理完成, orderId={}, orderStatus={}, paymentStatus={}",
                    result.orderId(),
                    result.orderStatus(),
                    result.paymentStatus());
            return result;
        } catch (DuplicateKeyException exception) {
            return recoverCompetingPayment(currentUserId, orderNo, idempotencyKey, exception);
        }
    }

    /** 支付响应未知时按本人订单号恢复，不自动重放支付。 */
    @Transactional(readOnly = true)
    public PaymentView queryPayment(String orderNo) {
        validateOrderNo(orderNo);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderSnapshot order = orderRepository.findByOrderNo(currentUserId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        PaymentRepository.PaymentSnapshot payment = paymentRepository.findPaymentByOrderId(order.orderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        PaymentRepository.TicketSnapshot ticket = paymentRepository.findTicketByOrderId(order.orderId())
                .orElse(null);
        return paymentViewFactory.create(order, payment, ticket);
    }

    private PaymentView recoverCompetingPayment(
            long userId,
            String orderNo,
            String idempotencyKey,
            DuplicateKeyException originalException) {
        OrderRepository.OrderSnapshot order = orderRepository.findByOrderNo(userId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        PaymentRepository.PaymentSnapshot paymentByKey = paymentRepository
                .findPaymentByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (paymentByKey != null && paymentByKey.orderId() != order.orderId()) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
        PaymentRepository.PaymentSnapshot payment = paymentRepository.findPaymentByOrderId(order.orderId())
                .orElseThrow(() -> originalException);
        PaymentRepository.TicketSnapshot ticket = paymentRepository.findTicketByOrderId(order.orderId())
                .orElse(null);
        return paymentViewFactory.create(order, payment, ticket);
    }

    private void validatePaymentInput(String orderNo, String idempotencyKey) {
        validateOrderNo(orderNo);
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAXIMUM_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateOrderNo(String orderNo) {
        if (orderNo == null
                || orderNo.isBlank()
                || orderNo.length() > MAXIMUM_ORDER_NUMBER_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
