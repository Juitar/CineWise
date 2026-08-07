package com.miaoyu.ticket.geo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.geo.domain.LocationGranularity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 地点文本必须由 B 确认，D 只处理唯一的结构化地理编码结果。 */
class GeocodingUserLocationAdapterTest {

    @Test
    void givenUniqueCityPoiAndAddress_whenResolving_thenPreservesProviderGranularity() {
        assertThat(adapter(LocationGranularity.CITY).fromPlaceText("长沙").granularity())
                .isEqualTo(LocationGranularity.CITY);
        assertThat(adapter(LocationGranularity.POI).fromPlaceText("五一广场").granularity())
                .isEqualTo(LocationGranularity.POI);
        assertThat(adapter(LocationGranularity.ADDRESS).fromPlaceText("岳麓大道 1 号").granularity())
                .isEqualTo(LocationGranularity.ADDRESS);
    }

    @Test
    void givenBlankOrAmbiguousPlace_whenResolving_thenRejectsWithoutChoosingCandidate() {
        GeocodingUserLocationAdapter ambiguous = new GeocodingUserLocationAdapter(text -> List.of(
                candidate(LocationGranularity.POI), candidate(LocationGranularity.POI)));

        assertThatThrownBy(() -> adapter(LocationGranularity.CITY).fromPlaceText(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ambiguous.fromPlaceText("人民广场"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GeocodingUserLocationAdapter adapter(LocationGranularity granularity) {
        return new GeocodingUserLocationAdapter(text -> List.of(candidate(granularity)));
    }

    private PlaceGeocodingPort.Candidate candidate(LocationGranularity granularity) {
        return new PlaceGeocodingPort.Candidate(
                new BigDecimal("112.938814"), new BigDecimal("28.228209"), granularity);
    }
}
