package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.ticketing.application.ShowContextQueryService;
import com.miaoyu.ticket.ticketing.application.ShowQueryRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 支付事务外解析事件展示上下文，D内容查询失败不能扩大支付事务。
 *
 * <ul>
 *   <li>订单和场次标识来自A的权威快照，不能接受前端提交；</li>
 *   <li>影院区域只能来自D公开摘要，不能跨模块读取内容表；</li>
 *   <li>外部摘要不进入订单、支付或电子票事务；</li>
 *   <li>无法形成完整事件时返回空，由PAID订单对账补偿。</li>
 * </ul>
 */
@Service
public class PaymentEventContextResolver {

    private final ShowContextQueryService showContextQueryService;
    private final ContentSummaryQueryPort contentSummaryQueryPort;

    public PaymentEventContextResolver(
            ShowContextQueryService showContextQueryService,
            ContentSummaryQueryPort contentSummaryQueryPort) {
        this.showContextQueryService = showContextQueryService;
        this.contentSummaryQueryPort = contentSummaryQueryPort;
    }

    /**
     * 只接受未过期且区域非空的D公开摘要。
     * 过期或缺失摘要不应被伪造成出行事实，支付成功后由对账流程重建事件。
     *
     * <p>场次查询和内容查询分开执行，是为了保持A的排期事实与D的展示事实各自权威；
     * 这里仅按cinemaId组合只读快照，不保存内容副本。</p>
     */
    public Optional<PaymentEventContext> resolve(OrderRepository.OrderSnapshot order) {
        Optional<ShowQueryRepository.ShowContext> showContext =
                showContextQueryService.findContext(order.showId());
        if (showContext.isEmpty()) {
            return Optional.empty();
        }

        ShowQueryRepository.ShowContext show = showContext.get();
        Map<Long, ContentSummaryQueryPort.CinemaSummary> summaries =
                contentSummaryQueryPort.findCinemaSummaries(Set.of(show.cinemaId()));
        ContentSummaryQueryPort.CinemaSummary cinema = summaries.get(show.cinemaId());
        if (!isUsable(cinema)) {
            return Optional.empty();
        }
        return Optional.of(new PaymentEventContext(show.showId(), cinema.area().trim(), show.startTime()));
    }

    /** 过期标识由D按统一Clock计算，A不自行重算另一套内容有效期。 */
    private boolean isUsable(ContentSummaryQueryPort.CinemaSummary cinema) {
        return cinema != null
                && !cinema.expired()
                && cinema.area() != null
                && !cinema.area().isBlank();
    }

    /**
     * 进入支付事务的不可变快照，仅包含冻结事件仍缺少的场次展示字段。
     * showId用于在锁单后再次核对上下文归属，避免预查询结果被错误绑定到另一场次。
     */
    public record PaymentEventContext(long showId, String cinemaArea, LocalDateTime startAt) {
    }
}
