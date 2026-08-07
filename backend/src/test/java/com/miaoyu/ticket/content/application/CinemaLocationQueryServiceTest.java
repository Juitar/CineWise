package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class CinemaLocationQueryServiceTest {

    private static final long CINEMA_ID = 1001L;
    private static final LocalDateTime DATA_TIME = LocalDateTime.of(2026, 8, 8, 9, 0);

    @Test
    void givenLiveCinemaWithValidCoordinates_whenFindingLocation_thenItReturnsPoint() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.query(Mockito.any(ContentQuery.class))).thenReturn(result(ContentSourceType.LIVE,
                new BigDecimal("112.938814"), new BigDecimal("28.228209")));

        CinemaLocationQueryService service = new CinemaLocationQueryService(contents);

        assertThat(service.findByCinemaId(CINEMA_ID)).hasValueSatisfying(point -> {
            assertThat(point.longitude()).isEqualByComparingTo("112.938814");
            assertThat(point.latitude()).isEqualByComparingTo("28.228209");
        });
        assertThat(service.findAreaByCinemaId(CINEMA_ID)).contains("岳麓区");
    }

    @Test
    void givenDemoCinemaWithCoordinates_whenFindingLocation_thenItReturnsNothing() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.query(Mockito.any(ContentQuery.class))).thenReturn(result(ContentSourceType.MOCK,
                new BigDecimal("112.938814"), new BigDecimal("28.228209")));

        CinemaLocationQueryService service = new CinemaLocationQueryService(contents);

        // Demo 坐标只用于离线展示，不能作为路线、天气或餐饮 Provider 的真实地点输入。
        assertThat(service.findByCinemaId(CINEMA_ID)).isEmpty();
        assertThat(service.findAreaByCinemaId(CINEMA_ID)).isEmpty();
    }

    @Test
    void givenLiveCinemaWithInvalidCoordinates_whenFindingLocation_thenItReturnsNothing() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.query(Mockito.any(ContentQuery.class))).thenReturn(result(ContentSourceType.LIVE,
                new BigDecimal("181"), new BigDecimal("28.228209")));

        CinemaLocationQueryService service = new CinemaLocationQueryService(contents);

        assertThat(service.findByCinemaId(CINEMA_ID)).isEmpty();
    }

    @Test
    void givenNonPositiveCinemaId_whenFindingLocation_thenItDoesNotQueryContent() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        CinemaLocationQueryService service = new CinemaLocationQueryService(contents);

        assertThat(service.findByCinemaId(0L)).isEmpty();
        assertThat(service.findAreaByCinemaId(-1L)).isEmpty();
        verifyNoInteractions(contents);
    }

    @Test
    void givenUnavailableContentDirectory_whenFindingLocation_thenItKeepsDataUnavailable() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        BusinessException unavailable = new BusinessException(TestContentErrorCode.DATA_UNAVAILABLE);
        when(contents.query(Mockito.any(ContentQuery.class)))
                .thenThrow(unavailable);
        CinemaLocationQueryService service = new CinemaLocationQueryService(contents);

        // 内容查询故障不能被转换为空位置，否则调用方会误判为该影院没有真实坐标。
        assertThatThrownBy(() -> service.findByCinemaId(CINEMA_ID))
                .isSameAs(unavailable);
    }

    private ContentResult<List<? extends ContentItem>> result(
            ContentSourceType sourceType, BigDecimal longitude, BigDecimal latitude) {
        return new ContentResult<>(List.of(new CinemaContent(CINEMA_ID, "cinema-source-1001", "测试影院",
                "430100", "岳麓区", "长沙市岳麓区测试路", longitude, latitude)),
                new ContentSource(sourceType == ContentSourceType.LIVE ? "NETSTART_MAOYAN" : "DEMO_CONTENT",
                        sourceType),
                DATA_TIME, DATA_TIME.plusHours(6), false,
                sourceType != ContentSourceType.LIVE,
                sourceType == ContentSourceType.LIVE ? null : ContentFallbackType.MOCK);
    }

    /** 模拟内容服务的固定不可用码，用于确认位置查询不会吞掉上游错误。 */
    private enum TestContentErrorCode implements ErrorCode {
        DATA_UNAVAILABLE;

        @Override public int code() { return 303004; }
        @Override public String message() { return "内容数据暂不可用"; }
        @Override public HttpStatus httpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
    }
}
