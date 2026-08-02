package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 页面和后续票务工具共用的订单应用入口。 */
@Service
public class OrderApplicationService {

    private static final int MAXIMUM_KEY_LENGTH = 64;
    private static final int MAXIMUM_TICKET_COUNT = 6;

    private final CurrentUserAccessor currentUserAccessor;
    private final OrderCreationTransaction creationTransaction;
    private final OrderIdempotencyService idempotencyService;

    public OrderApplicationService(
            CurrentUserAccessor currentUserAccessor,
            OrderCreationTransaction creationTransaction,
            OrderIdempotencyService idempotencyService) {
        this.currentUserAccessor = currentUserAccessor;
        this.creationTransaction = creationTransaction;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 并发重复请求若在事务内竞争失败，先等待事务代理完成回滚，
     * 再按原请求键查询已提交结果，避免把真实幂等重试误报为座位冲突。
     */
    public OrderView createOrder(CreateOrderCommand rawCommand) {
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        CreateOrderCommand command = normalizeAndValidate(rawCommand);
        try {
            return creationTransaction.create(currentUserId, command);
        } catch (DuplicateKeyException exception) {
            return recoverAfterCompetingWrite(currentUserId, command, exception);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != TicketingErrorCode.SEAT_NOT_LOCKABLE) {
                throw exception;
            }
            return recoverAfterCompetingWrite(currentUserId, command, exception);
        }
    }

    /** 按当前用户和原客户端请求标识恢复丢失响应的订单。 */
    public OrderView queryByClientRequestId(String clientRequestId) {
        validateKey(clientRequestId);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        return idempotencyService.findByClientRequestId(currentUserId, clientRequestId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderView recoverAfterCompetingWrite(
            long userId,
            CreateOrderCommand command,
            RuntimeException originalException) {
        return idempotencyService.findMatchingOrder(userId, command)
                .orElseThrow(() -> originalException);
    }

    private CreateOrderCommand normalizeAndValidate(CreateOrderCommand command) {
        if (command == null || command.showId() <= 0 || command.seatIds() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        validateKey(command.clientRequestId());
        validateKey(command.idempotencyKey());
        if (command.seatIds().isEmpty()
                || command.seatIds().size() > MAXIMUM_TICKET_COUNT
                || command.seatIds().stream().anyMatch(seatId -> seatId == null || seatId <= 0)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        List<Long> sortedSeatIds = command.seatIds().stream()
                .sorted()
                .toList();
        if (sortedSeatIds.stream().distinct().count() != sortedSeatIds.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return new CreateOrderCommand(
                command.showId(),
                List.copyOf(sortedSeatIds),
                command.clientRequestId(),
                command.idempotencyKey());
    }

    private void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAXIMUM_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
