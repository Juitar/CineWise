package com.miaoyu.ticket.travel.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;

/** 高德原始响应只停留在基础设施层，不能进入应用服务或日志。 */
@FunctionalInterface
interface AmapRouteClient {
    JsonNode queryDrivingRoute(String origin, String destination, String key);
}
