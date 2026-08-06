package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** 推荐按城市取得影院候选的 D 公开应用服务，不读取内容持久层实现。 */
@Service
public class RecommendationContentCandidateQueryService {
    private final ContentQueryService contentQueryService;

    public RecommendationContentCandidateQueryService(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    /** 返回影院 ID、展示名、坐标和内容时效；内容不可用由内容模块稳定报错，不伪装为空列表。 */
    public List<CinemaCandidate> listCinemas(String cityCode) {
        var result = contentQueryService.query(
                new ContentQuery(ContentResourceType.CINEMA, null, cityCode, null));
        return result.data().stream().map(CinemaContent.class::cast)
                .filter(cinema -> cinema.cinemaId() != null)
                .map(cinema -> new CinemaCandidate(
                        cinema.cinemaId(), cinema.name(), cinema.longitude(), cinema.latitude(),
                        result.source().name(), result.dataTime(), result.expiresAt(), result.expired()))
                .toList();
    }

    public record CinemaCandidate(long cinemaId, String name, BigDecimal longitude, BigDecimal latitude, String source,
            LocalDateTime dataAt, LocalDateTime expiresAt, boolean expired) { }
}
