package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import org.junit.jupiter.api.Test;

class NetStartContentMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final NetStartContentMapper mapper = new NetStartContentMapper();

    @Test
    void givenSearchAddressWithCountyLevelCity_whenMapping_thenItUsesTheRegisteredArea() throws Exception {
        CinemaContent cinema = (CinemaContent) mapper.map(ContentResourceType.CINEMA, JSON.readTree("""
                {"id":41479,"info":{"name":"浏阳测试影城",
                "address":"浏阳市镇头镇华嘉时代广场B1栋412号"}}
                """), "430100").orElseThrow();

        assertThat(cinema.area()).isEqualTo("浏阳市");
    }

    @Test
    void givenDetailAddressWithCountyLevelCity_whenMapping_thenItUsesTheSameAreaRule() throws Exception {
        CinemaContent cinema = mapper.mapCinemaDetail(JSON.readTree("""
                {"data":{"cinemaId":41479,"nm":"浏阳测试影城",
                "addr":"浏阳市镇头镇华嘉时代广场B1栋412号"}}
                """), "430100").orElseThrow();

        assertThat(cinema.area()).isEqualTo("浏阳市");
    }
}
