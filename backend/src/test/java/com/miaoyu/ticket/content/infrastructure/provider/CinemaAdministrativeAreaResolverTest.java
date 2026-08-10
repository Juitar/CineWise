package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CinemaAdministrativeAreaResolverTest {

    private final CinemaAdministrativeAreaResolver resolver = new CinemaAdministrativeAreaResolver();

    @Test
    void givenChangshaAddresses_whenResolving_thenItRecognizesDistrictCountyAndCountyLevelCity() {
        assertThat(resolver.resolve("430100", "长沙市岳麓区梅溪湖路1号")).isEqualTo("岳麓区");
        assertThat(resolver.resolve("430100", "长沙县星沙大道88号")).isEqualTo("长沙县");
        assertThat(resolver.resolve("430100", "浏阳市镇头镇华嘉时代广场B1栋412号")).isEqualTo("浏阳市");
    }

    @Test
    void givenRegisteredHangzhouArea_whenResolving_thenItReturnsTheFullAreaName() {
        assertThat(resolver.resolve("330100", "杭州市滨江区江南大道100号")).isEqualTo("滨江区");
    }

    @Test
    void givenUnregisteredPlaceNameOrCity_whenResolving_thenItDoesNotGuessAnAdministrativeArea() {
        assertThat(resolver.resolve("430100", "万家丽住宅区1号")).isEqualTo("未知区域");
        assertThat(resolver.resolve("999999", "浏阳市镇头镇华嘉时代广场B1栋412号")).isEqualTo("未知区域");
    }
}
