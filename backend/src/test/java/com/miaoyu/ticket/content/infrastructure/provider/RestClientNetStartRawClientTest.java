package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RestClientNetStartRawClientTest {

    @Test
    void givenChangshaAdministrativeCode_whenBuildingProviderRequest_thenUseNetStartCityId() {
        assertThat(RestClientNetStartRawClient.providerCityId("430100")).isEqualTo("70");
    }

    @Test
    void givenHangzhouAdministrativeCode_whenBuildingProviderRequest_thenUseNetStartCityId() {
        assertThat(RestClientNetStartRawClient.providerCityId("330100")).isEqualTo("50");
    }

    @Test
    void givenUnsupportedAdministrativeCode_whenBuildingProviderRequest_thenRejectInsteadOfGuessing() {
        assertThatThrownBy(() -> RestClientNetStartRawClient.providerCityId("110100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已核对");
    }
}
