package com.miaoyu.ticket.travel.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AmapWeatherAdcodeAdapterTest {

    @Test
    void givenCinemaCoordinates_whenResolvingAdcode_thenCallAmapReverseGeocodingEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://restapi.amap.com/v3/geocode/regeo"
                        + "?location=113.008095%2C28.113579&key=test-key&extensions=base"))
                .andRespond(withSuccess("""
                        {"status":"1","infocode":"10000","regeocode":{
                          "addressComponent":{"adcode":"430111"}}}
                        """, APPLICATION_JSON));

        AmapWeatherAdcodeAdapter adapter = new AmapWeatherAdcodeAdapter(
                new AmapWeatherProperties(true, "test-key", Duration.ofMinutes(15), Map.of()),
                builder.build());

        assertThat(adapter.resolve(new ResolvedGeoPoint(
                new BigDecimal("113.008095"), new BigDecimal("28.113579"),
                LocationGranularity.ADDRESS))).contains("430111");
        server.verify();
    }
}
