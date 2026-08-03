package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 对order模块公开退票所需的最小场次事实。
 *
 * <ul>
 *   <li>退票截止以原场次开场时间为准；</li>
 *   <li>影片ID仅用于同影片替代场次过滤；</li>
 *   <li>查询失败不会回滚已经提交的退款；</li>
 *   <li>空候选保持空数组，不创建临时Mock场次。</li>
 * </ul>
 * <p>服务不返回影片标题等D内容字段，也不把替代查询并入退款写事务。</p>
 */
@Service
public class RefundShowService {

    private final RefundShowRepository repository;

    public RefundShowService(RefundShowRepository repository) {
        this.repository = repository;
    }

    /** 场次不存在属于交易数据损坏，由调用方映射为不可恢复一致性错误。 */
    public RefundShowRepository.RefundShowContext requireContext(long showId) {
        return repository.findRefundShowContext(showId)
                .orElseThrow(() -> new IllegalStateException("退票订单关联场次不存在"));
    }

    /** 空候选保持为空，调用方不得用Mock数据伪造替代场次。 */
    public List<RefundShowRepository.AlternativeShowSnapshot> queryAlternatives(
            RefundShowRepository.AlternativeShowCriteria criteria) {
        return repository.findAlternativeShows(criteria);
    }

    /** 开场时间必须严格晚于当前业务时间，等于边界时不再允许退票。 */
    public boolean hasNotStarted(long showId, LocalDateTime now) {
        return requireContext(showId).startTime().isAfter(now);
    }
}
