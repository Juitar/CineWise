package com.miaoyu.ticket.agent.application.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrowserCityResolutionServiceTest {

    private final CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
    private final BrowserCityResolver cityResolver = mock(BrowserCityResolver.class);
    private BrowserCityResolutionService service;

    @BeforeEach
    void setUp() {
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        service = new BrowserCityResolutionService(currentUserAccessor, cityResolver);
    }

    @Test
    void shouldReturnOnlyResolvedChineseCityForCurrentUser() {
        BigDecimal longitude = new BigDecimal("112.938815");
        BigDecimal latitude = new BigDecimal("28.228209");
        when(cityResolver.resolveCity(longitude, latitude)).thenReturn(Optional.of("长沙市"));

        assertThat(service.resolveCurrentUserCity(longitude, latitude)).isEqualTo("长沙市");

        verify(currentUserAccessor).requireCurrentUserId();
        verify(cityResolver).resolveCity(longitude, latitude);
    }

    @Test
    void shouldRejectOutOfRangeCoordinateWithoutCallingThirdParty() {
        assertThatThrownBy(() -> service.resolveCurrentUserCity(new BigDecimal("180.000001"), BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.CITY_RESOLUTION_UNAVAILABLE);

        verify(cityResolver, never()).resolveCity(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldHideExternalFailureAndNonChineseResponse() {
        when(cityResolver.resolveCity(new BigDecimal("112.9"), new BigDecimal("28.2")))
                .thenReturn(Optional.of("Changsha"));

        assertThatThrownBy(() -> service.resolveCurrentUserCity(new BigDecimal("112.9"), new BigDecimal("28.2")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.CITY_RESOLUTION_UNAVAILABLE);
    }
}
