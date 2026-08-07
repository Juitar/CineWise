package com.miaoyu.ticket.travel.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientAmapRouteClientTest {

    @Test
    void givenDrivingMode_whenQuery_thenUseDrivingEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://amap.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAmapRouteClient client = new RestClientAmapRouteClient(builder.build());
        server.expect(requestTo("https://amap.test/v3/direction/driving?origin=120.1%2C30.2&destination=120.2%2C30.3"
                + "&strategy=0&key=test-key"))
                .andRespond(withSuccess("{\"status\":\"1\"}", MediaType.APPLICATION_JSON));

        assertThat(client.queryDrivingRoute("120.1,30.2", "120.2,30.3", "test-key").path("status").asText())
                .isEqualTo("1");
        server.verify();
    }

    @Test
    void givenWalkingMode_whenQuery_thenUseWalkingEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://amap.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAmapRouteClient client = new RestClientAmapRouteClient(builder.build());
        server.expect(requestTo("https://amap.test/v3/direction/walking?origin=120.1%2C30.2&destination=120.2%2C30.3"
                + "&key=test-key"))
                .andRespond(withSuccess("{\"status\":\"1\"}", MediaType.APPLICATION_JSON));

        assertThat(client.queryWalkingRoute("120.1,30.2", "120.2,30.3", "test-key").path("status").asText())
                .isEqualTo("1");
        server.verify();
    }
}
