package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.RefundShowRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 将退票场次投影映射为ticketing/application公开类型。
 *
 * <ul>
 *   <li>不存在原场次映射为Optional.empty；</li>
 *   <li>候选顺序沿用数据库稳定排序；</li>
 *   <li>映射不补造内容字段或可售状态；</li>
 *   <li>返回列表由Stream终结为不可变语义快照。</li>
 * </ul>
 */
@Repository
public class MybatisRefundShowRepository implements RefundShowRepository {

    private final RefundShowMapper mapper;

    public MybatisRefundShowRepository(RefundShowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RefundShowContext> findRefundShowContext(long showId) {
        RefundShowContextRow row = mapper.findRefundShowContext(showId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new RefundShowContext(
                row.showId(),
                row.movieId(),
                row.cinemaId(),
                row.startTime()));
    }

    @Override
    public List<AlternativeShowSnapshot> findAlternativeShows(AlternativeShowCriteria criteria) {
        return mapper.findAlternativeShows(criteria).stream()
                .map(row -> new AlternativeShowSnapshot(
                        row.showId(),
                        row.cinemaId(),
                        row.startTime(),
                        row.basePrice(),
                        row.status(),
                        row.availableSeatCount()))
                .toList();
    }
}
