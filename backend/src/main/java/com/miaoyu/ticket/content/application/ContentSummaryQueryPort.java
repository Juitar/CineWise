package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/** D 负责的公开内容摘要端口；票务模块据此补充影院展示字段，不直接读取 D 的持久层。 */
public interface ContentSummaryQueryPort {

    /** 批量查询影院摘要，未找到的 ID 不出现在结果中。 */
    Map<Long, CinemaSummary> findCinemaSummaries(Set<Long> cinemaIds);

    record CinemaSummary(long cinemaId, String name, String source, LocalDateTime dataTime) {
    }
}
