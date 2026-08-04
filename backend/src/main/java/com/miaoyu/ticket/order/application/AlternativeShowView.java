package com.miaoyu.ticket.order.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 同影片替代场次的票务权威字段。
 *
 * <ul>
 *   <li>showId、movieId、cinemaId和startTime来自A拥有的movie_show；</li>
 *   <li>basePrice仅表示候选场次当前基础票价；</li>
 *   <li>availableSeatCount是查询时快照，不承诺后续锁座成功；</li>
 *   <li>影片标题、影院名称等内容字段仍由D的公开端口提供。</li>
 * </ul>
 */
public record AlternativeShowView(
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime startTime,
        BigDecimal basePrice,
        String status,
        int availableSeatCount) {
}
