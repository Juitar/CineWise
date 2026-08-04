package com.miaoyu.ticket.admin.application;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.application.OrderErrorCode;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理订单只读用例，负责权限复核、筛选规范化和跨聚合批量组装。
 *
 * <p>Spring Security是第一道入口保护，本服务仍从CurrentUserAccessor复核ADMIN，
 * 防止未来被Job、其他Controller或模块内部代码绕过URL规则直接调用。</p>
 *
 * <p>用户筛选和脱敏只委托C公开端口；交易数据只委托A只读Repository。
 * 两类依赖都失败时保持原错误语义，不把故障降级成“没有订单”。</p>
 *
 * <p>列表执行顺序固定为：</p>
 * <ol>
 *   <li>先确认当前身份是ADMIN，避免越权请求触发任何数据查询；</li>
 *   <li>再规范化白名单条件，拒绝任意排序、无界日期和非法业务ID；</li>
 *   <li>存在用户关键字时先向C换取最多100个用户ID；</li>
 *   <li>查询A订单总数和当前页主行，不在SQL中访问认证表；</li>
 *   <li>按当前页ID批量加载三个交易摘要和脱敏用户信息；</li>
 *   <li>最后在内存组装不可变视图，不在循环中继续访问数据库。</li>
 * </ol>
 *
 * <p>详情与列表共享同一主投影，避免金额、状态和场次字段出现两套解释。
 * 唯一区别是详情额外加载订单座位快照，列表不为不可见字段付出查询成本。</p>
 */
@Service
public class AdminOrderQueryService {

    private static final int MAXIMUM_ORDER_NUMBER_LENGTH = 32;
    private static final int MAXIMUM_EMAIL_KEYWORD_LENGTH = 100;
    private static final int MINIMUM_EMAIL_KEYWORD_LENGTH = 2;
    private static final int MAXIMUM_DATE_RANGE_DAYS = 31;
    private static final int MAXIMUM_MATCHED_USER_IDS = 100;

    private final CurrentUserAccessor currentUserAccessor;
    private final UserAdminQueryPort userAdminQueryPort;
    private final AdminOrderRepository repository;
    private final ApiProperties apiProperties;

    public AdminOrderQueryService(
            CurrentUserAccessor currentUserAccessor,
            UserAdminQueryPort userAdminQueryPort,
            AdminOrderRepository repository,
            ApiProperties apiProperties) {
        this.currentUserAccessor = currentUserAccessor;
        this.userAdminQueryPort = userAdminQueryPort;
        this.repository = repository;
        this.apiProperties = apiProperties;
    }

    /**
     * 查询稳定分页，并在取得当前页后才批量补充用户和交易摘要。
     *
     * <p>用户关键字无匹配时立即返回空分页，避免把空ID集合错误地解释成
     * “不启用用户筛选”而执行全表订单查询。</p>
     */
    @Transactional(readOnly = true)
    public AdminOrderPageView queryOrders(AdminOrderListQuery query) {
        requireAdministrator();
        NormalizedQuery normalized = normalizeQuery(query);
        Set<Long> matchedUserIds = resolveMatchedUserIds(normalized.userKeyword());
        if (normalized.userKeyword() != null && matchedUserIds.isEmpty()) {
            return emptyPage(normalized);
        }

        AdminOrderRepository.Criteria criteria = normalized.toCriteria(matchedUserIds);
        long total = repository.countOrders(criteria);
        if (total == 0) {
            return emptyPage(normalized);
        }

        List<AdminOrderRepository.OrderSnapshot> orders = repository.findOrderPage(criteria);
        if (orders.isEmpty()) {
            // 页码超出最后一页不是数据故障，仍返回真实total和空records。
            return new AdminOrderPageView(total, normalized.page(), normalized.size(), List.of());
        }

        BatchContext context = loadBatchContext(orders, false);
        List<AdminOrderView> records = orders.stream()
                .map(order -> assemble(order, context))
                .toList();
        return new AdminOrderPageView(total, normalized.page(), normalized.size(), records);
    }

    /** 管理详情不按普通用户归属过滤，但仍必须先完成应用层ADMIN复核。 */
    @Transactional(readOnly = true)
    public AdminOrderView queryOrder(String orderNo) {
        requireAdministrator();
        String normalizedOrderNo = normalizeRequiredOrderNo(orderNo);
        AdminOrderRepository.OrderSnapshot order = repository.findByOrderNo(normalizedOrderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return assemble(order, loadBatchContext(List.of(order), true));
    }

    /**
     * 执行模块内部第二层权限防线。
     *
     * <p>未认证由C的CurrentUserAccessor保留401语义；已经认证但角色不是ADMIN时，
     * 本服务返回403。检查发生在参数解析和Repository调用之前，防止普通用户利用
     * 参数错误、订单存在性或响应耗时探测管理数据。</p>
     */
    private void requireAdministrator() {
        CurrentUser currentUser = currentUserAccessor.requireCurrentUser();
        if (currentUser.role() != RoleCode.ADMIN) {
            // 角色只信任C写入的安全上下文，不读取查询参数或请求体声明。
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 把REST原始参数一次转换为Repository可执行条件。
     *
     * <p>该方法集中维护trim、枚举、业务ID、日期和分页规则，避免Controller、
     * Mapper和不同用例各自形成略有差异的校验。只有成功返回的条件才可以进入SQL。</p>
     *
     * <p>dateTo按页面常见的“包含结束日”解释，再转换为次日零点之前的左闭右开区间，
     * 从而不依赖数据库时间精度拼接23:59:59.999。</p>
     */
    private NormalizedQuery normalizeQuery(AdminOrderListQuery query) {
        if (query == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        String orderNo = normalizeOptionalOrderNo(query.orderNo());
        String userKeyword = normalizeUserKeyword(query.userKeyword());
        OrderStatus status = parseStatus(query.status());
        Long movieId = parseOptionalBusinessId(query.movieId());
        Long showId = parseOptionalBusinessId(query.showId());
        validateDateRange(query.dateFrom(), query.dateTo());

        int page = query.page() == null ? apiProperties.defaultPage() : query.page();
        int size = query.size() == null ? apiProperties.defaultPageSize() : query.size();
        long offset = (long) (page - 1) * size;
        if (page < 1 || size < 1 || size > apiProperties.maxPageSize() || offset > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }

        LocalDateTime createdAtOrAfter = query.dateFrom() == null
                ? null
                : query.dateFrom().atStartOfDay();
        LocalDateTime createdBefore = query.dateTo() == null
                ? null
                : query.dateTo().plusDays(1).atStartOfDay();
        return new NormalizedQuery(
                orderNo,
                userKeyword,
                status,
                movieId,
                showId,
                createdAtOrAfter,
                createdBefore,
                page,
                size,
                (int) offset);
    }

    /**
     * 仅在用户筛选实际启用时调用C端口。
     *
     * <p>null表示完全没有筛选条件；空Set表示条件合法但没有匹配用户。两者不能混用，
     * 否则空匹配会被Repository误解成查询所有订单。</p>
     *
     * <p>数量和正数检查是跨模块防御，不重新实现C的匹配规则。契约损坏时直接失败关闭，
     * 绝不把超过100个ID送入动态IN条件。</p>
     */
    private Set<Long> resolveMatchedUserIds(String userKeyword) {
        if (userKeyword == null) {
            return Set.of();
        }
        Set<Long> userIds = Objects.requireNonNull(
                userAdminQueryPort.findUserIdsByKeyword(userKeyword),
                "C用户查询端口不得返回null");
        if (userIds.size() > MAXIMUM_MATCHED_USER_IDS
                || userIds.stream().anyMatch(userId -> userId == null || userId <= 0)) {
            // 跨模块契约被破坏时失败关闭，绝不把无界或非法ID传入订单IN条件。
            throw new IllegalStateException("C用户查询端口返回了非法ID集合");
        }
        // A复制为不可变集合，防止后续适配器修改已经校验的查询条件。
        return Set.copyOf(userIds);
    }

    /**
     * 为当前页建立一次性批量上下文。
     *
     * <p>页面大小最多100，因此用户ID和订单ID天然有界。用户摘要只传本页去重ID，
     * 不把筛选阶段命中的全部用户再次交给C。</p>
     *
     * <p>列表批量读取支付、票和退款状态；详情额外读取座位快照。每种关联固定一次查询，
     * 查询次数只随关联种类变化，不随订单数量线性增长。</p>
     *
     * <p>C返回Map缺少用户是合法历史状态；整个Map为null则是契约破坏，不能降级为
     * 所有邮箱均不可用。C抛出的201010或301002保持原样向外传播。</p>
     */
    private BatchContext loadBatchContext(
            List<AdminOrderRepository.OrderSnapshot> orders,
            boolean includeSeats) {
        List<Long> orderIds = orders.stream()
                .map(AdminOrderRepository.OrderSnapshot::orderId)
                .toList();
        Set<Long> userIds = orders.stream()
                .map(AdminOrderRepository.OrderSnapshot::userId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, UserAdminQueryPort.UserAdminSummary> users = Objects.requireNonNull(
                userAdminQueryPort.findByUserIds(Set.copyOf(userIds)),
                "C用户摘要端口不得返回null");
        Map<Long, List<AdminOrderRepository.SeatSnapshot>> seats = includeSeats
                ? groupSeats(repository.findSeatsByOrderIds(orderIds))
                : Map.of();
        Map<Long, AdminOrderRepository.PaymentSnapshot> payments = uniqueIndex(
                repository.findPaymentsByOrderIds(orderIds),
                AdminOrderRepository.PaymentSnapshot::orderId);
        Map<Long, AdminOrderRepository.TicketSnapshot> tickets = uniqueIndex(
                repository.findTicketsByOrderIds(orderIds),
                AdminOrderRepository.TicketSnapshot::orderId);
        Map<Long, AdminOrderRepository.RefundSnapshot> refunds = uniqueIndex(
                repository.findRefundsByOrderIds(orderIds),
                AdminOrderRepository.RefundSnapshot::orderId);
        return new BatchContext(users, seats, payments, tickets, refunds);
    }

    /**
     * 将一个权威订单主行与已加载的内存索引合并。
     *
     * <p>该方法不得访问Repository或C端口，因此循环组装不会形成隐藏的N+1查询。
     * 用户摘要缺失时只把emailMasked设为null，订单和交易状态继续保留。</p>
     *
     * <p>所有金额、状态和时间都来自A权威交易快照；管理页面没有字段可以反向触发
     * 订单、支付、电子票、退款或座位状态迁移。</p>
     */
    private AdminOrderView assemble(
            AdminOrderRepository.OrderSnapshot order,
            BatchContext context) {
        UserAdminQueryPort.UserAdminSummary user = context.users().get(order.userId());
        AdminOrderRepository.PaymentSnapshot payment = context.payments().get(order.orderId());
        AdminOrderRepository.TicketSnapshot ticket = context.tickets().get(order.orderId());
        AdminOrderRepository.RefundSnapshot refund = context.refunds().get(order.orderId());
        List<AdminOrderView.SeatView> seats = context.seats()
                .getOrDefault(order.orderId(), List.of())
                .stream()
                .map(seat -> new AdminOrderView.SeatView(
                        seat.seatId(), seat.rowNo(), seat.seatNo(), seat.unitPrice()))
                .toList();
        return new AdminOrderView(
                order.orderId(),
                order.orderNo(),
                order.userId(),
                user == null ? null : user.emailMasked(),
                order.showId(),
                order.movieId(),
                order.cinemaId(),
                order.showStartTime(),
                order.ticketCount(),
                order.unitPrice(),
                order.totalAmount(),
                order.status(),
                order.expireTime(),
                order.paidTime(),
                order.cancelledTime(),
                order.refundedTime(),
                order.version(),
                order.createdAt(),
                order.updatedAt(),
                seats,
                toPaymentView(payment),
                toTicketView(ticket),
                toRefundView(refund));
    }

    /**
     * 将支付投影转换为公开应用视图。
     *
     * <p>null表示订单尚未创建支付记录，不等于支付失败。固定成功Mock支付没有可由
     * 管理查询推导出的失败终态。</p>
     */
    private AdminOrderView.PaymentView toPaymentView(AdminOrderRepository.PaymentSnapshot payment) {
        if (payment == null) {
            return null;
        }
        return new AdminOrderView.PaymentView(
                payment.paymentNo(),
                payment.amount(),
                payment.status(),
                payment.requestedAt(),
                payment.paidAt(),
                payment.version(),
                payment.updatedAt());
    }

    /**
     * 将电子票投影转换为不含二维码载荷的应用视图。
     *
     * <p>null表示订单尚未出票；票状态必须直接使用权威枚举，不能从订单状态猜测。</p>
     */
    private AdminOrderView.TicketView toTicketView(AdminOrderRepository.TicketSnapshot ticket) {
        if (ticket == null) {
            return null;
        }
        return new AdminOrderView.TicketView(
                ticket.ticketCode(),
                ticket.status(),
                ticket.issuedAt(),
                ticket.invalidatedAt(),
                ticket.version(),
                ticket.updatedAt());
    }

    /**
     * 将退款投影转换为不含内部确认字段的应用视图。
     *
     * <p>null表示订单没有退款记录；原因可以为空，调用方不能用空原因判断退款结果。</p>
     */
    private AdminOrderView.RefundView toRefundView(AdminOrderRepository.RefundSnapshot refund) {
        if (refund == null) {
            return null;
        }
        return new AdminOrderView.RefundView(
                refund.refundNo(),
                refund.reason(),
                refund.status(),
                refund.requestedAt(),
                refund.processedAt(),
                refund.version(),
                refund.updatedAt());
    }

    /**
     * 按订单ID保存座位的数据库排序结果。
     *
     * <p>Mapper已按order_id、show_seat_id排序，本方法保持插入顺序，确保同一详情在
     * 重复查询时返回稳定座位顺序。</p>
     */
    private Map<Long, List<AdminOrderRepository.SeatSnapshot>> groupSeats(
            List<AdminOrderRepository.SeatSnapshot> seats) {
        Map<Long, List<AdminOrderRepository.SeatSnapshot>> grouped = new HashMap<>();
        for (AdminOrderRepository.SeatSnapshot seat : seats) {
            grouped.computeIfAbsent(seat.orderId(), ignored -> new ArrayList<>()).add(seat);
        }
        return grouped;
    }

    /**
     * 把数据库唯一关联转换为按订单ID索引。
     *
     * <p>支付、电子票和退款都有每订单唯一约束。若脏数据返回重复记录，收集器应抛错，
     * 不能随意选择第一条或最后一条并向管理员伪造确定状态。</p>
     */
    private <T> Map<Long, T> uniqueIndex(List<T> values, Function<T, Long> keyExtractor) {
        // 数据库唯一约束应保证一订单一记录；重复值直接失败，不能静默挑选一条伪造权威状态。
        return values.stream().collect(Collectors.toUnmodifiableMap(keyExtractor, Function.identity()));
    }

    /**
     * 可选订单号的空值表示不启用筛选。
     *
     * <p>非空值继续复用必填订单号规则，避免列表和详情允许不同长度。</p>
     */
    private String normalizeOptionalOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return normalizeRequiredOrderNo(orderNo);
    }

    /**
     * 订单号只执行trim和长度边界校验。
     *
     * <p>SQL使用等值参数绑定，应用层不接受通配符或客户端排序表达式。</p>
     */
    private String normalizeRequiredOrderNo(String orderNo) {
        if (orderNo == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        String normalized = orderNo.trim();
        if (normalized.isEmpty() || normalized.length() > MAXIMUM_ORDER_NUMBER_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return normalized;
    }

    /**
     * 区分“参数未出现”和“显式提交空白值”。
     *
     * <p>数字关键字的溢出、0和超长语义由C统一处理为无匹配；A只对非数字邮箱片段
     * 执行2至100字符的公开接口边界校验，不复制邮箱规范化或LIKE转义实现。</p>
     */
    private String normalizeUserKeyword(String userKeyword) {
        if (userKeyword == null) {
            // 参数未出现表示不启用筛选，与显式提交空白字符串不同。
            return null;
        }
        String normalized = userKeyword.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        boolean asciiDigits = normalized.chars().allMatch(character -> character >= '0' && character <= '9');
        if (!asciiDigits && (normalized.length() < MINIMUM_EMAIL_KEYWORD_LENGTH
                || normalized.length() > MAXIMUM_EMAIL_KEYWORD_LENGTH)) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        return normalized;
    }

    /**
     * 只接受冻结的OrderStatus枚举名称。
     *
     * <p>展示文案不可以作为查询值，避免数据库出现无法由状态机产生的任意字符串。</p>
     */
    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "订单状态不合法");
        }
    }

    /**
     * 把REST字符串业务ID转换为正long。
     *
     * <p>前端仍使用字符串防止JavaScript精度丢失；只有进入应用查询条件时才解析。</p>
     */
    private Long parseOptionalBusinessId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "业务ID必须是正整数");
        }
    }

    /**
     * 限制管理查询最多覆盖31个自然日。
     *
     * <p>限制同时保护普通无筛选索引查询和管理页面误操作，不允许用管理接口执行
     * 无界历史数据扫描。只提供单侧边界时仍允许按索引查询全部之前或之后的数据。</p>
     */
    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (LocalDate.MAX.equals(dateTo)) {
            // 结束日需要转换为次日零点；MAX无法形成左闭右开上界，必须在日期运算前拒绝。
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "结束日期超出可查询范围");
        }
        if (dateFrom == null || dateTo == null) {
            return;
        }
        long inclusiveDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (inclusiveDays < 1 || inclusiveDays > MAXIMUM_DATE_RANGE_DAYS) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "日期范围最多31天");
        }
    }

    /** 空结果保留已规范化分页参数，便于前端维持筛选上下文。 */
    private AdminOrderPageView emptyPage(NormalizedQuery query) {
        return new AdminOrderPageView(0, query.page(), query.size(), List.of());
    }

    /**
     * 规范化条件只在服务内部流转，避免Repository再次解释REST字符串。
     *
     * <p>userKeyword只用于判断userIds的null语义，不会进入SQL或日志；真正订单查询
     * 只携带C返回的有限用户ID集合。</p>
     */
    private record NormalizedQuery(
            String orderNo,
            String userKeyword,
            OrderStatus status,
            Long movieId,
            Long showId,
            LocalDateTime createdAtOrAfter,
            LocalDateTime createdBefore,
            int page,
            int size,
            int offset) {

        /** 将有用户筛选的空集合与未启用筛选的null条件明确分开。 */
        AdminOrderRepository.Criteria toCriteria(Set<Long> userIds) {
            return new AdminOrderRepository.Criteria(
                    orderNo,
                    userKeyword == null ? null : userIds,
                    status,
                    movieId,
                    showId,
                    createdAtOrAfter,
                    createdBefore,
                    offset,
                    size);
        }
    }

    /**
     * 当前页全部外部摘要的内存索引，保证组装阶段不再触发数据库调用。
     *
     * <p>Map只在单次请求事务中存活，不写入缓存或持久化第二份用户、支付、票和退款索引。</p>
     */
    private record BatchContext(
            Map<Long, UserAdminQueryPort.UserAdminSummary> users,
            Map<Long, List<AdminOrderRepository.SeatSnapshot>> seats,
            Map<Long, AdminOrderRepository.PaymentSnapshot> payments,
            Map<Long, AdminOrderRepository.TicketSnapshot> tickets,
            Map<Long, AdminOrderRepository.RefundSnapshot> refunds) {
    }
}
