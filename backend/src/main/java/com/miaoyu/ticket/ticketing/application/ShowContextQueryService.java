package com.miaoyu.ticket.ticketing.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向A的订单用例公开最小场次事实，避免订单模块依赖票务Mapper或Entity。
 *
 * <p>服务不返回票价和库存，因为支付仍以订单金额快照和锁定座位为权威；这里的场次数据只用于构造
 * 支付成功通知，不参与是否允许支付的状态判断。</p>
 */
@Service
public class ShowContextQueryService {

    private final ShowQueryRepository repository;

    public ShowContextQueryService(ShowQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 缺失场次保持为空，由调用用例决定阻断交易还是进入补偿流程。
     * 支付事件调用方选择补偿，其他交易用例仍可对数据损坏采用更严格的拒绝策略。
     */
    @Transactional(readOnly = true)
    public Optional<ShowContextView> findContext(long showId) {
        return repository.findShowContext(showId)
                .map(context -> new ShowContextView(
                        context.showId(),
                        context.movieId(),
                        context.cinemaId(),
                        context.startTime()));
    }
}
