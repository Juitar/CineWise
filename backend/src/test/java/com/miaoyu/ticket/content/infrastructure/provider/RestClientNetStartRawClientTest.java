package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientNetStartRawClientTest {

    @Test
    void givenCinemaId_whenFetchingCinemaDetail_thenItUsesTheDetailEndpointAndQueryParameter() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://netstart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientNetStartRawClient client = new RestClientNetStartRawClient(builder.build());
        server.expect(requestTo("https://netstart.test/cinema/detail?cinemaId=40843"))
                .andRespond(withSuccess("{\"data\":{\"cinemaId\":40843}}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCinemaDetail(40843L).path("data").path("cinemaId").asLong()).isEqualTo(40843L);
        server.verify();
    }

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
