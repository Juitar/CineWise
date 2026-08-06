package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentCityCinemaQueryServiceTest {

    @Test
    void givenResolvedCity_whenLookup_thenItReturnsOnlyLocalIds() {
        CityResolutionService resolver = mock(CityResolutionService.class);
        ContentCityCinemaQueryPort port = mock(ContentCityCinemaQueryPort.class);
        when(resolver.resolve("长沙")).thenReturn(new CityResolutionService.CityResolution(
                CityResolutionService.Status.RESOLVED, "长沙"));
        when(port.findActiveCinemaIds("长沙")).thenReturn(List.of(11L, 12L));

        assertThat(new ContentCityCinemaQueryService(resolver, port).findCinemaIds("长沙"))
                .containsExactly(11L, 12L);
    }

    @Test
    void givenUnresolvedCity_whenLookup_thenItDoesNotReadOtherCities() {
        CityResolutionService resolver = mock(CityResolutionService.class);
        ContentCityCinemaQueryPort port = mock(ContentCityCinemaQueryPort.class);
        when(resolver.resolve("不存在")).thenReturn(CityResolutionService.CityResolution.unrecognized());

        assertThatThrownBy(() -> new ContentCityCinemaQueryService(resolver, port).findCinemaIds("不存在"))
                .isInstanceOf(com.miaoyu.ticket.common.error.BusinessException.class);
        org.mockito.Mockito.verifyNoInteractions(port);
    }

    @Test
    void givenResolvedCityWithoutLocalCinema_whenLookup_thenItReturnsNormalEmptyResult() {
        CityResolutionService resolver = mock(CityResolutionService.class);
        ContentCityCinemaQueryPort port = mock(ContentCityCinemaQueryPort.class);
        when(resolver.resolve("杭州")).thenReturn(new CityResolutionService.CityResolution(
                CityResolutionService.Status.RESOLVED, "杭州"));
        when(port.findActiveCinemaIds("杭州")).thenReturn(List.of());

        assertThat(new ContentCityCinemaQueryService(resolver, port).findCinemaIds("杭州")).isEmpty();
    }
}
