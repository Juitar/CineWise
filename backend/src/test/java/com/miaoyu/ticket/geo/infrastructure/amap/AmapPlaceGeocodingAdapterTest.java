package com.miaoyu.ticket.geo.infrastructure.amap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.travel.infrastructure.weather.AmapWeatherProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 使用实测高德地理编码 JSON，验证 location 顺序、level 映射和失败响应处理。 */
class AmapPlaceGeocodingAdapterTest {

    @Test
    void givenUniquePoiResponse_whenGeocoding_thenMapLongitudeLatitudeAndPoiGranularity() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://restapi.amap.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://restapi.amap.com/v3/geocode/geo?address=%E9%95%BF%E6%B2%99%20%E4%BA%94%E4%B8%80%E5%B9%BF%E5%9C%BA&key=test-key"))
                .andRespond(withSuccess(success("住宅区"), MediaType.APPLICATION_JSON));

        var candidates = adapter(builder.build()).geocode("长沙 五一广场");

        assertThat(candidates).singleElement().satisfies(item -> {
            assertThat(item.longitude()).isEqualByComparingTo("112.987363");
            assertThat(item.latitude()).isEqualByComparingTo("28.194129");
            assertThat(item.granularity()).isEqualTo(LocationGranularity.POI);
        });
        server.verify();
    }

    @Test
    void givenProviderFailure_whenGeocoding_thenReturnNoCandidate() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://restapi.amap.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://restapi.amap.com/v3/geocode/geo?address=x&key=test-key"))
                .andRespond(withSuccess("{\"status\":\"0\",\"infocode\":\"20001\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter(builder.build()).geocode("x")).isEmpty();
        server.verify();
    }

    private AmapPlaceGeocodingAdapter adapter(RestClient client) {
        return new AmapPlaceGeocodingAdapter(
                new AmapWeatherProperties(true, "test-key", Duration.ofMinutes(15), Map.of()), client);
    }

    private String success(String level) {
        return "{\"status\":\"1\",\"infocode\":\"10000\",\"geocodes\":[{"
                + "\"location\":\"112.987363,28.194129\",\"level\":\""
                + level + "\"}]}";
    }
}
