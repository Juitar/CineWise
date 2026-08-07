package com.miaoyu.ticket.order.application;

import java.time.LocalDateTime;
import java.util.List;

/** 自动生命周期任务使用的最小电子票持久化端口。 */
public interface ElectronicTicketLifecycleRepository {

    /** 返回本轮最多处理的有效票候选，候选本身不作为状态迁移依据。 */
    List<ShowEndedTicketCandidate> findShowEndedTicketCandidates(
            LocalDateTime endedAtOrBefore,
            int limit);

    /**
     * 仅在票仍有效、订单仍已支付且场次仍结束时完成失效。
     *
     * <p>状态、版本、订单及结束时间全部放在同一条SQL中，防止重复任务或并发退款覆盖权威结果。</p>
     */
    boolean invalidateAfterShowEnd(
            long ticketId,
            int expectedVersion,
            LocalDateTime endedAtOrBefore,
            LocalDateTime invalidatedAt);

    /** 有界扫描的最小投影，不携带二维码、用户信息或订单金额。 */
    record ShowEndedTicketCandidate(long ticketId, int ticketVersion) {

        public ShowEndedTicketCandidate {
            if (ticketId <= 0 || ticketVersion < 0) {
                throw new IllegalArgumentException("电子票结束失效候选包含非法标识或版本");
            }
        }
    }
}
