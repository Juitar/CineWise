package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class CityResolutionServiceTest {

    private final CityResolutionService service = new CityResolutionService(
            new ObjectMapper(), new DefaultResourceLoader());

    @Test
    void givenChangshaTemporaryLocationText_whenResolve_thenOnlyCityNameIsReturned() {
        CityResolutionService.CityResolution result = service.resolve("湖南省长沙市岳麓区");

        assertThat(result.status()).isEqualTo(CityResolutionService.Status.RESOLVED);
        assertThat(result.cityName()).isEqualTo("长沙");
        assertThat(service.findProviderCityId(result.cityName())).contains("70");
    }

    @Test
    void givenHangzhouTemporaryLocationText_whenResolve_thenItUsesThePackagedCatalogWithoutNetStartCall() {
        CityResolutionService.CityResolution result = service.resolve("浙江省杭州市西湖区");

        assertThat(result.status()).isEqualTo(CityResolutionService.Status.RESOLVED);
        assertThat(result.cityName()).isEqualTo("杭州");
        assertThat(service.findProviderCityId(result.cityName())).contains("50");
    }

    @Test
    void givenNoOrMultipleCities_whenResolve_thenNoLocationDetailIsReturned() {
        assertThat(service.resolve("未知地点").status()).isEqualTo(CityResolutionService.Status.UNRECOGNIZED);
        CityResolutionService.CityResolution multiple = service.resolve("长沙到杭州");
        assertThat(multiple.status()).isEqualTo(CityResolutionService.Status.SELECTION_REQUIRED);
        assertThat(multiple.cityName()).isNull();
    }

    @Test
    void givenMalformedCatalog_whenResolve_thenItReturnsStableUnavailableErrorWithoutUsingLocationText() {
        CityResolutionService unavailableService = new CityResolutionService(
                new ObjectMapper(), resourceLoader("not json"));

        assertThatThrownBy(() -> unavailableService.resolve("湖南省长沙市岳麓区"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    @Test
    void givenDuplicateCityNameOrProviderId_whenLoadCatalog_thenItRejectsTheCatalog() {
        String duplicateCatalog = """
                {"catalogVersion":"v1","source":"fixture","checkedAt":"2026-08-05","cities":[
                  {"cityName":"长沙","providerCityId":"70"},
                  {"cityName":"长沙","providerCityId":"50"}
                ]}
                """;
        CityResolutionService unavailableService = new CityResolutionService(
                new ObjectMapper(), resourceLoader(duplicateCatalog));

        assertThatThrownBy(() -> unavailableService.resolve("长沙"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    /** 目录通过 ResourceLoader 注入，测试不会访问 NetStart 或任何外部网络。 */
    private ResourceLoader resourceLoader(String resourceContent) {
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(resourceContent.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
    }
}
