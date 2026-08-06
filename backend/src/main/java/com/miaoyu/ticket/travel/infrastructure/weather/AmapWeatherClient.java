package com.miaoyu.ticket.travel.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 高德 HTTP 调用边界。
 *
 * <p>应用层只看到标准天气结果；原始 JSON、请求 URL 和服务端密钥都限制在基础设施层，避免进入任务快照
 * 或业务日志。</p>
 */
interface AmapWeatherClient {
    JsonNode query(String cityAdcode, String key);
}
