package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * D 负责的公开内容摘要端口；票务模块据此补充影院展示字段，不直接读取 D 的持久层。
 *
 * <p>该端口是同一应用内的 Java 调用，不是 HTTP 接口。A 只能依赖此 DTO，不能依赖 D 的 SQL、表结构
 * 或缓存格式，这样 D 后续替换内容来源不会影响 A。</p>
 *
 * <p>摘要保留过期标识，A 在支付后准备出行建议时能知道影院资料是否仍可作为当前展示依据。</p>
 */
public interface ContentSummaryQueryPort {

    /**
     * 批量查询影院摘要，未找到的 ID 不出现在结果中。
     *
     * <p>Map 使用 cinemaId 作为键，避免 A 按输入集合顺序错误关联影院名称和区域。</p>
     */
    Map<Long, CinemaSummary> findCinemaSummaries(Set<Long> cinemaIds);

    /**
     * A 只拿到展示需要的影院摘要，不暴露 D 的持久化对象。
     *
     * <p>area 是行政区域展示字段；地址和经纬度不在本 DTO 中，防止支付后流程扩大为路线定位数据读取。</p>
     */
    record CinemaSummary(
            long cinemaId,
            String name,
            String area,
            String source,
            LocalDateTime dataTime,
            LocalDateTime expiresAt,
            boolean expired) {
    }
}
