package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 按影院标识读取已确认的静态坐标。
 *
 * <p>影院位置不是用户输入，因而不经过 UserLocationAdapter。该服务只使用内容模块公开查询服务，
 * 不向 travel、recommendation 或 Provider 暴露内容的 Entity、Mapper、Repository。</p>
 */
@Service
public class CinemaLocationQueryService {

    private final ContentQueryService contentQueryService;

    public CinemaLocationQueryService(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    /**
     * 返回可用于地图 Provider 的影院坐标；缺失、越界或过精度的资料统一视为不可用。
     *
     * <p>这里不根据 address 猜测坐标，也不用 0,0 占位。影院是静态地点，使用 ADDRESS 粒度仅表示
     * 可作为路线终点，不表示任何用户的当前位置。</p>
     */
    public Optional<ResolvedGeoPoint> findByCinemaId(long cinemaId) {
        if (cinemaId <= 0) {
            return Optional.empty();
        }
        return contentQueryService.query(new ContentQuery(ContentResourceType.CINEMA, cinemaId, null, null)).data()
                .stream().map(CinemaContent.class::cast)
                .filter(cinema -> cinemaId == cinema.cinemaId())
                .findFirst()
                .flatMap(cinema -> toPoint(cinema.longitude(), cinema.latitude()));
    }

    /**
     * 返回影院展示区域，仅供逆地理不可用时使用受控旧映射回退。
     *
     * <p>该字段不能再作为路线、附近餐饮或天气的正常查询位置。</p>
     */
    public Optional<String> findAreaByCinemaId(long cinemaId) {
        if (cinemaId <= 0) {
            return Optional.empty();
        }
        return contentQueryService.query(new ContentQuery(ContentResourceType.CINEMA, cinemaId, null, null)).data()
                .stream().map(CinemaContent.class::cast)
                .filter(cinema -> cinemaId == cinema.cinemaId()).map(CinemaContent::area).findFirst();
    }

    private Optional<ResolvedGeoPoint> toPoint(java.math.BigDecimal longitude, java.math.BigDecimal latitude) {
        try {
            return Optional.of(new ResolvedGeoPoint(longitude, latitude, LocationGranularity.ADDRESS));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
