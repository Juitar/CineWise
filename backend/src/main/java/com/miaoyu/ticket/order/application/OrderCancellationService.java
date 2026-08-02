package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.domain.OrderOperationType;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 页面取消订单的身份、参数和并发幂等恢复入口。 */
@Service
public class OrderCancellationService {

    private static final int MAXIMUM_KEY_LENGTH = 64;
    private static final int MAXIMUM_ORDER_NUMBER_LENGTH = 32;

    private final CurrentUserAccessor currentUserAccessor;
    private final OrderCancellationTransaction cancellationTransaction;
    private final OrderRepository repository;
    private final OrderViewFactory viewFactory;

    public OrderCancellationService(
            CurrentUserAccessor currentUserAccessor,
            OrderCancellationTransaction cancellationTransaction,
            OrderRepository repository,
            OrderViewFactory viewFactory) {
        this.currentUserAccessor = currentUserAccessor;
        this.cancellationTransaction = cancellationTransaction;
        this.repository = repository;
        this.viewFactory = viewFactory;
    }

    /** 唯一约束竞争失败后先回滚当前事务，再按原幂等键恢复已提交结果。 */
    public OrderView cancelOrder(String orderNo, String idempotencyKey) {
        validateInput(orderNo, idempotencyKey);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        try {
            return cancellationTransaction.cancel(currentUserId, orderNo, idempotencyKey);
        } catch (DuplicateKeyException exception) {
            return recoverCompetingCancellation(
                    currentUserId,
                    orderNo,
                    idempotencyKey,
                    exception);
        }
    }

    private OrderView recoverCompetingCancellation(
            long userId,
            String orderNo,
            String idempotencyKey,
            DuplicateKeyException originalException) {
        OrderRepository.OrderOperationSnapshot operation = repository.findOperation(
                        userId,
                        OrderOperationType.CANCEL,
                        idempotencyKey)
                .orElseThrow(() -> originalException);
        OrderRepository.OrderSnapshot order = repository.findByOrderNo(userId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        String expectedParameterHash = OrderOperationParameterHasher.hash(
                OrderOperationType.CANCEL,
                order.orderId());
        if (!Objects.equals(operation.orderNoSnapshot(), orderNo)
                || !Objects.equals(operation.parameterHash(), expectedParameterHash)) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
        return viewFactory.create(order);
    }

    private void validateInput(String orderNo, String idempotencyKey) {
        if (orderNo == null
                || orderNo.isBlank()
                || orderNo.length() > MAXIMUM_ORDER_NUMBER_LENGTH
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAXIMUM_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
