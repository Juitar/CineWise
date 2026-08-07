package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.content.application.ContentQuery;

/** 原始 HTTP 响应只允许停留在 Provider 基础设施层。 */
interface NetStartRawClient {
    JsonNode fetch(ContentQuery query);

    /** 影院搜索只保证身份和展示字段，详情接口用于补齐影院静态坐标。 */
    default JsonNode fetchCinemaDetail(long cinemaId) {
        return null;
    }
}
