package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.ticketing.application.ShowContextQueryService;
import com.miaoyu.ticket.ticketing.application.ShowContextView;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 在交易事务外解析支付和退款事件共用的出行展示上下文。
 *
 * <ul>
 *   <li>订单和场次标识来自A的权威快照，不能接受前端提交；</li>
 *   <li>影院区域只能来自D公开摘要，不能跨模块读取内容表；</li>
 *   <li>外部摘要不进入订单、支付、电子票或退款事务；</li>
 *   <li>无法形成完整事件时返回空，由权威订单对账补偿。</li>
 * </ul>
 */
@Service
public class TravelEventContextResolver {

    private final ShowContextQueryService showContextQueryService;
    private final ContentSummaryQueryPort contentSummaryQueryPort;

    public TravelEventContextResolver(
            ShowContextQueryService showContextQueryService,
            ContentSummaryQueryPort contentSummaryQueryPort) {
        this.showContextQueryService = showContextQueryService;
        this.contentSummaryQueryPort = contentSummaryQueryPort;
    }

    /**
     * 只接受未过期且区域非空的D公开摘要。
     * 过期或缺失摘要不应被伪造成出行事实，交易成功后由对账流程重建事件。
     *
     * <p>场次查询和内容查询分开执行，是为了保持A的排期事实与D的展示事实各自权威；
     * 这里仅按cinemaId组合只读快照，不保存内容副本。</p>
     */
    public Optional<TravelEventContext> resolve(OrderRepository.OrderSnapshot order) {
        Optional<ShowContextView> showContext = showContextQueryService.findContext(order.showId());
        if (showContext.isEmpty()) {
            return Optional.empty();
        }

        ShowContextView show = showContext.get();
        // cinemaId是A拥有的场次关联事实；非法值不能用内容摘要或默认值掩盖。
        if (show.showId() != order.showId() || show.cinemaId() <= 0) {
            return Optional.empty();
        }
        ContentSummaryQueryPort.CinemaSummary cinema = contentSummaryQueryPort
                .findCinemaSummaries(Set.of(show.cinemaId()))
                .findByCinemaId(show.cinemaId())
                .orElse(null);
        if (!isUsable(cinema)) {
            return Optional.empty();
        }
        return Optional.of(new TravelEventContext(
                show.showId(),
                show.movieId(),
                show.cinemaId(),
                cinema.area().trim(),
                show.startTime()));
    }

    /** 过期标识由D按统一Clock计算，A不自行重算另一套内容有效期。 */
    private boolean isUsable(ContentSummaryQueryPort.CinemaSummary cinema) {
        return cinema != null
                && !cinema.expired()
                && cinema.area() != null
                && !cinema.area().isBlank();
    }

    /**
     * 进入交易事务的不可变快照，仅包含冻结事件仍缺少的场次展示字段。
     * showId用于在锁单后再次核对上下文归属；cinemaId保持A权威场次关联，避免预查询结果被错误绑定
     * 到另一场次或由内容模块推测影院ID。
     */
    public record TravelEventContext(
            long showId,
            Long movieId,
            long cinemaId,
            String cinemaArea,
            LocalDateTime startAt) {

        public TravelEventContext {
            if (showId <= 0 || cinemaId <= 0) {
                throw new IllegalArgumentException("出行事件上下文包含非法场次或影院标识");
            }
        }
    }
}
