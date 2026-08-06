package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.RefundShowRepository;
import com.miaoyu.ticket.ticketing.application.RefundShowService;
import com.miaoyu.ticket.ticketing.application.SeatRefundService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退票输入、身份、影响查询、并发恢复和替代场次的应用入口。
 *
 * <ul>
 *   <li>当前用户只来自CurrentUserAccessor；</li>
 *   <li>订单号、原因和请求键先规范化再进入事务；</li>
 *   <li>退款金额、状态和场次时间始终从数据库重读；</li>
 *   <li>DuplicateKey只触发权威结果查询，不重放写事务；</li>
 *   <li>替代场次是事务外只读能力，失败不影响退票终态。</li>
 * </ul>
 * <p>固定页面REST不接管B拥有的Agent确认凭证。</p>
 */
@Service
public class RefundApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundApplicationService.class);
    private static final int MAXIMUM_ORDER_NUMBER_LENGTH = 32;
    private static final int MAXIMUM_REQUEST_KEY_LENGTH = 64;
    private static final int MAXIMUM_REASON_LENGTH = 255;
    private static final int MAXIMUM_DATE_RANGE_DAYS = 7;
    private static final String IMPACT_TEXT = "退票后订单和电子票将失效，座位恢复可售";

    private final CurrentUserAccessor currentUserAccessor;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundTransaction refundTransaction;
    private final RefundViewFactory refundViewFactory;
    private final RefundEligibilityPolicy eligibilityPolicy;
    private final RefundShowService refundShowService;
    private final SeatRefundService seatRefundService;
    private final TravelEventContextResolver travelEventContextResolver;
    private final Clock clock;

    public RefundApplicationService(
            CurrentUserAccessor currentUserAccessor,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundTransaction refundTransaction,
            RefundViewFactory refundViewFactory,
            RefundEligibilityPolicy eligibilityPolicy,
            RefundShowService refundShowService,
            SeatRefundService seatRefundService,
            TravelEventContextResolver travelEventContextResolver,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundTransaction = refundTransaction;
        this.refundViewFactory = refundViewFactory;
        this.eligibilityPolicy = eligibilityPolicy;
        this.refundShowService = refundShowService;
        this.seatRefundService = seatRefundService;
        this.travelEventContextResolver = travelEventContextResolver;
        this.clock = clock;
    }

    /** 影响查询与写事务使用相同资格策略，但查询绝不创建确认或退款记录。 */
    @Transactional(readOnly = true)
    public RefundImpactView queryImpact(String orderNo) {
        validateOrderNo(orderNo);
        long userId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderSnapshot order = requireOwnedOrder(userId, orderNo);
        PaymentRepository.PaymentSnapshot payment = requirePayment(order.orderId());
        PaymentRepository.TicketSnapshot ticket = requireTicket(order.orderId());
        RefundShowRepository.RefundShowContext show = refundShowService.requireContext(order.showId());
        int soldSeatCount = seatRefundService.countSoldSeats(order.orderId());
        eligibilityPolicy.requireRefundable(
                order,
                payment,
                ticket,
                show,
                soldSeatCount,
                currentBusinessTime());
        return new RefundImpactView(
                order.orderId(),
                order.orderNo(),
                order.totalAmount(),
                order.status(),
                ticket.status(),
                show.startTime(),
                order.version(),
                ticket.version(),
                IMPACT_TEXT);
    }

    /**
     * 唯一约束竞争失败后只读取已提交结果，不在已回滚事务中继续修改状态。
     */
    public RefundView requestRefund(RefundCommand rawCommand) {
        RefundCommand command = normalizeAndValidate(rawCommand);
        long userId = currentUserAccessor.requireCurrentUserId();
        Optional<TravelEventContextResolver.TravelEventContext> eventContext =
                resolveEventContext(userId, command.orderNo());
        // 外层服务捕获唯一约束竞争，确保原事务已经回滚后才查询竞争者提交的结果。
        // 这里不循环重试，避免对退款和座位释放形成隐式网络重放语义。
        try {
            RefundView result = refundTransaction.refund(userId, command, eventContext);
            LOGGER.info(
                    "模拟退票处理完成, orderId={}, refundStatus={}, orderStatus={}",
                    result.orderId(),
                    result.refundStatus(),
                    result.orderStatus());
            return result;
        } catch (DuplicateKeyException exception) {
            return recoverCompetingRefund(userId, command, exception);
        }
    }

    /**
     * D的影院摘要查询位于退款事务之外；暂时不可用时退款主链继续，由REFUNDED订单对账补偿。
     * 已退款重放不再查询D，避免恢复权威结果时产生无意义的跨模块依赖。
     */
    private Optional<TravelEventContextResolver.TravelEventContext> resolveEventContext(
            long userId,
            String orderNo) {
        OrderRepository.OrderSnapshot order = orderRepository.findByOrderNo(userId, orderNo).orElse(null);
        if (order == null || order.status() != OrderStatus.PAID) {
            return Optional.empty();
        }
        try {
            return travelEventContextResolver.resolve(order);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "退款失效事件上下文暂不可用, orderId={}, errorType={}",
                    order.orderId(),
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 响应未知时按本人订单号查询原退款，不自动生成新幂等键重放。 */
    @Transactional(readOnly = true)
    public RefundView queryRefund(String orderNo) {
        validateOrderNo(orderNo);
        long userId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderSnapshot order = requireOwnedOrder(userId, orderNo);
        RefundRepository.RefundSnapshot refund = refundRepository.findByOrderId(order.orderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        PaymentRepository.TicketSnapshot ticket = requireTicket(order.orderId());
        return refundViewFactory.create(order, refund, ticket);
    }

    /**
     * 替代场次是独立只读用例；空结果不会改变已经完成的退款。
     */
    @Transactional(readOnly = true)
    public AlternativeShowsView queryAlternativeShows(
            String orderNo,
            LocalDate dateFrom,
            LocalDate dateTo) {
        validateOrderNo(orderNo);
        LocalDateRange range = normalizeDateRange(dateFrom, dateTo);
        long userId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderSnapshot order = requireOwnedOrder(userId, orderNo);
        RefundShowRepository.RefundShowContext originalShow = refundShowService.requireContext(order.showId());
        LocalDateTime now = currentBusinessTime();
        RefundShowRepository.AlternativeShowCriteria criteria =
                new RefundShowRepository.AlternativeShowCriteria(
                        originalShow.movieId(),
                        originalShow.cinemaId(),
                        originalShow.showId(),
                        now,
                        range.dateFrom().atStartOfDay(),
                        range.dateTo().plusDays(1).atStartOfDay());
        return new AlternativeShowsView(
                order.orderNo(),
                refundShowService.queryAlternatives(criteria).stream()
                        .map(show -> new AlternativeShowView(
                                show.showId(),
                                originalShow.movieId(),
                                show.cinemaId(),
                                show.startTime(),
                                show.basePrice(),
                                show.status(),
                                show.availableSeatCount()))
                        .toList());
    }

    /** 并发唯一键恢复仍校验原键参数，不能把数据库冲突误报为成功。 */
    private RefundView recoverCompetingRefund(
            long userId,
            RefundCommand command,
            DuplicateKeyException originalException) {
        OrderRepository.OrderSnapshot order = requireOwnedOrder(userId, command.orderNo());
        RefundRepository.RefundSnapshot refundByKey = refundRepository
                .findByUserAndIdempotencyKey(userId, command.idempotencyKey())
                .orElse(null);
        if (refundByKey != null && refundByKey.orderId() != order.orderId()) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
        RefundRepository.RefundSnapshot refund = refundRepository.findByOrderId(order.orderId())
                .orElseThrow(() -> originalException);
        if (refund.idempotencyKey().equals(command.idempotencyKey())) {
            boolean same = refund.clientRequestId().equals(command.clientRequestId())
                    && java.util.Objects.equals(refund.refundReason(), command.refundReason())
                    && java.util.Objects.equals(refund.actionId(), command.actionId());
            if (!same) {
                throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
            }
        }
        return refundViewFactory.create(order, refund, requireTicket(order.orderId()));
    }

    /** REST出现actionId时拒绝，防止调用方把未验证字符串冒充B确认结果。 */
    private RefundCommand normalizeAndValidate(RefundCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        validateOrderNo(command.orderNo());
        String clientRequestId = normalizeRequired(command.clientRequestId(), MAXIMUM_REQUEST_KEY_LENGTH);
        String idempotencyKey = normalizeRequired(command.idempotencyKey(), MAXIMUM_REQUEST_KEY_LENGTH);
        String reason = normalizeOptional(command.refundReason(), MAXIMUM_REASON_LENGTH);
        String actionId = normalizeOptional(command.actionId(), MAXIMUM_REQUEST_KEY_LENGTH);
        if (actionId != null) {
            throw new BusinessException(OrderErrorCode.CONFIRMATION_INVALID);
        }
        return new RefundCommand(command.orderNo().trim(), reason, clientRequestId, null, idempotencyKey);
    }

    private LocalDateRange normalizeDateRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID));
        // 缺省窗口固定为含起始日在内的七天；显式窗口也不能超过同一上限。
        // 历史日期会让页面展示已失效场次，因此在应用边界直接拒绝。
        LocalDate normalizedFrom = dateFrom == null ? today : dateFrom;
        LocalDate normalizedTo = dateTo == null ? normalizedFrom.plusDays(MAXIMUM_DATE_RANGE_DAYS - 1) : dateTo;
        long days = ChronoUnit.DAYS.between(normalizedFrom, normalizedTo);
        if (normalizedFrom.isBefore(today) || days < 0 || days >= MAXIMUM_DATE_RANGE_DAYS) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return new LocalDateRange(normalizedFrom, normalizedTo);
    }

    private OrderRepository.OrderSnapshot requireOwnedOrder(long userId, String orderNo) {
        return orderRepository.findByOrderNo(userId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private PaymentRepository.PaymentSnapshot requirePayment(long orderId) {
        return paymentRepository.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_REFUNDABLE));
    }

    private PaymentRepository.TicketSnapshot requireTicket(long orderId) {
        return paymentRepository.findTicketByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_REFUNDABLE));
    }

    private void validateOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > MAXIMUM_ORDER_NUMBER_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private String normalizeRequired(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return value.trim();
    }

    private String normalizeOptional(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maximumLength) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return value.trim();
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    private record LocalDateRange(LocalDate dateFrom, LocalDate dateTo) {
    }
}
