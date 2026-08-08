package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RecommendationContentCandidateQueryServiceTest {
    @Test
    void mapsCityCinemaAndPreservesContentTime() {
        ContentQueryService service = Mockito.mock(ContentQueryService.class);
        LocalDateTime dataAt = LocalDateTime.of(2026, 8, 6, 10, 0);
        CinemaContent cinema = new CinemaContent(2L, "source-2", "测试影院", "430100", "岳麓区", "测试路",
                new BigDecimal("112.938814"), new BigDecimal("28.228209"));
        when(service.query(Mockito.any())).thenReturn(new ContentResult<>(List.of(cinema),
                new ContentSource("NETSTART", ContentSourceType.LIVE), dataAt,
                dataAt.plusHours(1), false, false, null));
        when(service.findLiveDemoPurchaseCatalog("430100")).thenReturn(new ContentPurchaseQueryPort.DemoPurchaseCatalog(
                List.of(), List.of(), "NETSTART_MAOYAN", OffsetDateTime.of(dataAt, ZoneOffset.UTC),
                OffsetDateTime.of(dataAt.plusHours(1), ZoneOffset.UTC)));
        var candidates = new RecommendationContentCandidateQueryService(service).listCinemas("430100");
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.cinemaId()).isEqualTo(2L);
            assertThat(candidate.name()).isEqualTo("测试影院");
            assertThat(candidate.source()).isEqualTo("NETSTART");
            assertThat(candidate.dataAt()).isEqualTo(dataAt);
        });
    }
}
