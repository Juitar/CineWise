package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

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
     * <p>返回列表按影院业务 ID 升序排列；缺失 ID 单独返回，调用方不能把“没有这家影院”误判为“内容库不可读”。</p>
     */
    CinemaSummaryBatch findCinemaSummaries(Set<Long> cinemaIds);

    /**
     * 批量结果把命中项和缺失项同时返回，调用方无需根据临时 Map 猜测“影院不存在”还是“查询失败”。
     * 整体内容存储不可读时实现必须抛出 303004，不能伪装成全量缺失。
     */
    record CinemaSummaryBatch(List<CinemaSummary> cinemas, Set<Long> missingCinemaIds) {
        public CinemaSummaryBatch {
            cinemas = List.copyOf(cinemas);
            missingCinemaIds = Set.copyOf(missingCinemaIds);
        }

        public java.util.Optional<CinemaSummary> findByCinemaId(long cinemaId) {
            return cinemas.stream().filter(cinema -> cinema.cinemaId() == cinemaId).findFirst();
        }
    }

    /** 内容存储整体不可读时的固定错误，调用方据此与正常的 missingCinemaIds 区分。 */
    enum ContentSummaryErrorCode implements ErrorCode {
        DATA_UNAVAILABLE;

        @Override public int code() { return 303004; }
        @Override public String message() { return "内容数据暂不可用"; }
        @Override public HttpStatus httpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
    }

    /**
     * A 只拿到展示需要的影院摘要，不暴露 D 的持久化对象。
     *
     * <p>address 是购票页展示地址，可以为空；经纬度不在本 DTO 中，防止购票流程扩大为路线定位数据读取。</p>
     */
    record CinemaSummary(
            long cinemaId,
            String name,
            String area,
            String address,
            String dataSource,
            LocalDateTime dataTime,
            LocalDateTime expiresAt,
            boolean expired) {
    }
}
