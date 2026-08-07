package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import org.springframework.web.client.RestClient;

/**
 * NetStart 的最小 HTTP 适配器。
 *
 * <p>只请求公开影片详情、热映列表和影院搜索接口；不调用影评、选座或任何交易接口，原始 JSON
 * 立即交给同包映射器，不能越过这里传给 Application。</p>
 */
final class RestClientNetStartRawClient implements NetStartRawClient {
    private static final String CHANGSHA_CITY_CODE = "430100";
    private static final String CHANGSHA_PROVIDER_CITY_ID = "70";
    private static final String HANGZHOU_CITY_CODE = "330100";
    private static final String HANGZHOU_PROVIDER_CITY_ID = "50";
    private final RestClient restClient;

    RestClientNetStartRawClient(RestClient restClient) { this.restClient = restClient; }

    @Override
    public JsonNode fetch(ContentQuery query) {
        if (query.resourceType() == ContentResourceType.MOVIE) {
            return query.contentId() == null
                    ? restClient.get().uri("/index/movieOnInfoList").retrieve().body(JsonNode.class)
                    : restClient.get().uri(uri -> uri.path("/movie/detail")
                    .queryParam("movieId", query.contentId()).build()).retrieve().body(JsonNode.class);
        }
        return restClient.get().uri(uri -> uri.path("/search/cinemas")
                .queryParam("keyword", query.keyword())
                .queryParam("ci", providerCityId(query.cityCode()))
                .build())
                .retrieve().body(JsonNode.class);
    }

    /** 第三方城市 ID 只能存在于 HTTP 边界，公开 DTO、快照和 URL 始终使用标准行政代码。 */
    static String providerCityId(String cityCode) {
        if (CHANGSHA_CITY_CODE.equals(cityCode)) {
            return CHANGSHA_PROVIDER_CITY_ID;
        }
        if (HANGZHOU_CITY_CODE.equals(cityCode)) {
            return HANGZHOU_PROVIDER_CITY_ID;
        }
        throw new IllegalArgumentException("NetStart 当前只支持已核对的长沙或杭州行政代码");
    }
}
