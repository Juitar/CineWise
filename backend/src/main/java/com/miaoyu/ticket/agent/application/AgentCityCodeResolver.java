package com.miaoyu.ticket.agent.application;

import java.util.Optional;

/** Agent 对外请求城市码解析的应用端口，内容模块负责提供权威实现。 */
public interface AgentCityCodeResolver {

    /** 无法唯一解析时返回空，调用方保留原槽位并按正常流程继续追问。 */
    Optional<String> resolveCityCode(String locationText);
}
