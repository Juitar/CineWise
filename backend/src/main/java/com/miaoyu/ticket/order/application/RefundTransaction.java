package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import com.miaoyu.ticket.ticketing.application.RefundShowRepository;
import com.miaoyu.ticket.ticketing.application.RefundShowService;
import com.miaoyu.ticket.ticketing.application.SeatRefundService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在同一本地事务中完成退款记录、订单、电子票和座位状态迁移。
 *
 * <ul>
 *   <li>先锁本人订单，序列化同一订单的重复退款；</li>
 *   <li>再核对幂等键和每订单唯一退款，支持原结果恢复；</li>
 *   <li>随后重读支付、票、场次和座位，页面摘要不作为写入依据；</li>
 *   <li>按退款、订单、票、座位的固定顺序执行条件迁移；</li>
 *   <li>最后重读数据库终态，响应不使用内存推算结果。</li>
 * </ul>
 * <p>固定写入顺序与受影响行数检查是并发安全的一部分，任何一步异常都会回滚此前写入。</p>
 */
@Service
public class RefundTransaction {

    private static final String REFUND_NUMBER_PREFIX = "RFD";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundViewFactory refundViewFactory;
    private final RefundEligibilityPolicy eligibilityPolicy;
    private final RefundShowService refundShowService;
    private final SeatRefundService seatRefundService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public RefundTransaction(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundViewFactory refundViewFactory,
            RefundEligibilityPolicy eligibilityPolicy,
            RefundShowService refundShowService,
            SeatRefundService seatRefundService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundViewFactory = refundViewFactory;
        this.eligibilityPolicy = eligibilityPolicy;
        this.refundShowService = refundShowService;
        this.seatRefundService = seatRefundService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 行锁使同一订单的重复退票串行化，数据库唯一约束处理跨订单幂等键竞争。
     */
    @Transactional
    public RefundView refund(long userId, RefundCommand command) {
        OrderRepository.OrderSnapshot order = orderRepository
                .findByOrderNoForUpdate(userId, command.orderNo())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        RefundRepository.RefundSnapshot refundByKey = refundRepository
                .findByUserAndIdempotencyKey(userId, command.idempotencyKey())
                .orElse(null);
        validateRefundByKey(order, command, refundByKey);

        RefundRepository.RefundSnapshot existingRefund = refundRepository
                .findByOrderId(order.orderId())
                .orElse(null);
        // 同订单换新键重试仍返回唯一退款；只有原键重放需要逐项比较原请求参数。
        // 这一规则与支付重放一致，避免网络层生成新键后触发第二次资源释放。
        if (existingRefund != null) {
            if (existingRefund.idempotencyKey().equals(command.idempotencyKey())) {
                requireSameParameters(existingRefund, command);
            }
            return createExistingResult(order, existingRefund);
        }

        LocalDateTime refundedAt = currentBusinessTime();
        PaymentRepository.PaymentSnapshot payment = requirePayment(order.orderId());
        PaymentRepository.TicketSnapshot ticket = requireTicket(order.orderId());
        RefundShowRepository.RefundShowContext show = refundShowService.requireContext(order.showId());
        int soldSeatCount = seatRefundService.countSoldSeats(order.orderId());
        eligibilityPolicy.requireRefundable(order, payment, ticket, show, soldSeatCount, refundedAt);

        long refundId = idGenerator.nextId();
        // REQUESTED记录先保存退款金额和请求快照，使后续每个状态迁移都有唯一聚合承载。
        // 数据库每订单唯一约束与行锁共同防止并发请求产生第二条退款。
        refundRepository.insertRequestedRefund(new RefundRepository.NewRefund(
                refundId,
                REFUND_NUMBER_PREFIX + refundId,
                order.orderId(),
                userId,
                command.idempotencyKey(),
                command.clientRequestId(),
                command.refundReason(),
                command.actionId(),
                order.totalAmount(),
                order.version(),
                ticket.version(),
                show.startTime(),
                refundedAt));

        requireTransition(
                orderRepository.markOrderRefunding(order.orderId(), order.version(), refundedAt),
                "订单进入退款处理中失败");
        requireTransition(
                refundRepository.markProcessing(refundId, 0, refundedAt),
                "退款进入处理中失败");
        requireTransition(
                paymentRepository.refundTicket(ticket.ticketId(), ticket.version(), refundedAt),
                "电子票退款失效失败");

        int releasedSeatCount = seatRefundService.releaseSoldSeats(order.orderId(), refundedAt);
        // SQL可能在发现损坏数据前更新部分座位，因此必须抛异常让整个本地事务回滚。
        // 不能接受“退了几张算几张”，Mock退款也必须保持金额、票数和库存一致。
        if (releasedSeatCount != order.ticketCount()) {
            throw new IllegalStateException("退款订单的已售座位归属不完整，已回滚");
        }
        requireTransition(
                refundRepository.markSuccess(refundId, 1, refundedAt),
                "退款完成状态迁移失败");
        requireTransition(
                orderRepository.markOrderRefunded(order.orderId(), order.version() + 1, refundedAt),
                "订单退款完成状态迁移失败");
        return loadAuthoritativeResult(order.orderId());
    }

    /** 同键命中其他订单时必须在读取任何目标退款状态前拒绝。 */
    private void validateRefundByKey(
            OrderRepository.OrderSnapshot order,
            RefundCommand command,
            RefundRepository.RefundSnapshot refundByKey) {
        if (refundByKey == null) {
            return;
        }
        if (refundByKey.orderId() != order.orderId()) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
        requireSameParameters(refundByKey, command);
    }

    /** 原幂等键重放必须保持全部业务参数一致，空原因按null统一比较。 */
    private void requireSameParameters(
            RefundRepository.RefundSnapshot existing,
            RefundCommand command) {
        boolean same = Objects.equals(existing.clientRequestId(), command.clientRequestId())
                && Objects.equals(existing.refundReason(), command.refundReason())
                && Objects.equals(existing.actionId(), command.actionId());
        if (!same) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
    }

    /** 已存在退款只能以完整一致的终态恢复，禁止掩盖半事务数据。 */
    private RefundView createExistingResult(
            OrderRepository.OrderSnapshot order,
            RefundRepository.RefundSnapshot refund) {
        PaymentRepository.TicketSnapshot ticket = requireTicket(order.orderId());
        if (refund.status() != RefundStatus.SUCCESS
                || order.status() != OrderStatus.REFUNDED
                || ticket.status() != ElectronicTicketStatus.REFUNDED) {
            throw new IllegalStateException("既有退款与订单或电子票终态不一致");
        }
        return refundViewFactory.create(order, refund, ticket);
    }

    /** 事务内重新读取最终状态，返回值不依赖内存中推算的版本。 */
    private RefundView loadAuthoritativeResult(long orderId) {
        OrderRepository.OrderSnapshot order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("退款后订单丢失"));
        RefundRepository.RefundSnapshot refund = refundRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("退款后记录丢失"));
        PaymentRepository.TicketSnapshot ticket = requireTicket(orderId);
        return refundViewFactory.create(order, refund, ticket);
    }

    private PaymentRepository.PaymentSnapshot requirePayment(long orderId) {
        return paymentRepository.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("已支付订单缺少支付记录"));
    }

    private PaymentRepository.TicketSnapshot requireTicket(long orderId) {
        return paymentRepository.findTicketByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("订单缺少电子票"));
    }

    private void requireTransition(boolean transitioned, String message) {
        if (!transitioned) {
            throw new IllegalStateException(message + "，已回滚");
        }
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }
}
